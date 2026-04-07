package com.wpanther.xmlsigning.infrastructure.adapter.out.storage;

import com.wpanther.xmlsigning.domain.model.DocumentType;
import com.wpanther.xmlsigning.domain.model.SigningStatus;
import com.wpanther.xmlsigning.infrastructure.persistence.JpaSignedXmlDocumentRepository;
import com.wpanther.xmlsigning.infrastructure.persistence.SignedXmlDocumentEntity;
import com.wpanther.xmlsigning.infrastructure.storage.MinioStorageService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrphanedMinioFileCleanupScheduler")
class OrphanedMinioFileCleanupSchedulerTest {

    @Mock
    private JpaSignedXmlDocumentRepository documentRepository;

    @Mock
    private MinioStorageService minioStorageService;

    private MeterRegistry meterRegistry;

    private OrphanedMinioFileCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        scheduler = new OrphanedMinioFileCleanupScheduler(
                documentRepository, minioStorageService, meterRegistry);
        ReflectionTestUtils.setField(scheduler, "stuckThresholdHours", 1);
        ReflectionTestUtils.setField(scheduler, "cleanupEnabled", true);
    }

    @Nested
    @DisplayName("cleanupOrphanedFiles")
    class CleanupOrphanedFiles {

        @Test
        @DisplayName("skips cleanup when disabled")
        void skipsCleanupWhenDisabled() {
            ReflectionTestUtils.setField(scheduler, "cleanupEnabled", false);

            scheduler.cleanupOrphanedFiles();

            verifyNoInteractions(documentRepository);
            verifyNoInteractions(minioStorageService);
        }

        @Test
        @DisplayName("calls both cleanup strategies when enabled")
        void callsBothStrategies() {
            when(documentRepository.findStuckInSigning(any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());
            when(minioStorageService.listAllObjectKeys()).thenReturn(Collections.emptyList());

            scheduler.cleanupOrphanedFiles();

            verify(documentRepository).findStuckInSigning(any(LocalDateTime.class));
            verify(minioStorageService).listAllObjectKeys();
        }
    }

    @Nested
    @DisplayName("cleanupStuckSigningDocuments")
    class CleanupStuckSigningDocuments {

        @Test
        @DisplayName("does nothing when no stuck documents found")
        void doesNothingWhenNoStuckDocuments() {
            when(documentRepository.findStuckInSigning(any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            int deleted = scheduler.cleanupStuckSigningDocuments();

            assertThat(deleted).isZero();
            verify(minioStorageService, never()).delete(anyString());
        }

        @Test
        @DisplayName("deletes MinIO files and marks document as failed")
        void deletesFilesAndMarksFailed() {
            SignedXmlDocumentEntity doc = createStuckDocument("inv-001", "/path/to/original.xml");
            when(documentRepository.findStuckInSigning(any(LocalDateTime.class)))
                    .thenReturn(List.of(doc));

            int deleted = scheduler.cleanupStuckSigningDocuments();

            assertThat(deleted).isEqualTo(1);
            verify(minioStorageService).delete("/path/to/original.xml");
            verify(documentRepository).save(argThat(entity ->
                    entity.getStatus() == SigningStatus.FAILED &&
                            entity.getErrorMessage() != null &&
                            entity.getErrorMessage().contains("Orphaned")
            ));
        }

        @Test
        @DisplayName("continues cleaning other documents when one fails")
        void continuesWhenOneFails() {
            SignedXmlDocumentEntity doc1 = createStuckDocument("inv-001", "/path/to/first.xml");
            SignedXmlDocumentEntity doc2 = createStuckDocument("inv-002", "/path/to/second.xml");
            when(documentRepository.findStuckInSigning(any(LocalDateTime.class)))
                    .thenReturn(List.of(doc1, doc2));

            // First delete throws, second succeeds
            doThrow(new RuntimeException("MinIO error"))
                    .doNothing()
                    .when(minioStorageService).delete(anyString());

            int deleted = scheduler.cleanupStuckSigningDocuments();

            // Only 1 succeeds because first one threw and we continue without saving failed docs
            assertThat(deleted).isEqualTo(1);
            // findStuckInSigning is called once (returns both docs), then iterates
            verify(documentRepository, times(1)).findStuckInSigning(any(LocalDateTime.class));
            // Only doc2 (second) gets saved after successful delete; doc1 delete fails so no save
            verify(documentRepository, times(1)).save(any(SignedXmlDocumentEntity.class));
        }

        private SignedXmlDocumentEntity createStuckDocument(String documentId, String originalXmlPath) {
            return SignedXmlDocumentEntity.builder()
                    .id(UUID.randomUUID())
                    .documentId(documentId)
                    .documentNumber("INV-001")
                    .documentType(DocumentType.INVOICE)
                    .originalXmlPath(originalXmlPath)
                    .status(SigningStatus.SIGNING)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now().minusHours(2))
                    .updatedAt(LocalDateTime.now().minusHours(2))
                    .build();
        }
    }

    @Nested
    @DisplayName("reconcileMinioFiles")
    class ReconcileMinioFiles {

        @Test
        @DisplayName("does nothing when MinIO bucket is empty")
        void doesNothingWhenBucketEmpty() {
            when(minioStorageService.listAllObjectKeys()).thenReturn(Collections.emptyList());

            int deleted = scheduler.reconcileMinioFiles();

            assertThat(deleted).isZero();
            verify(minioStorageService, never()).delete(anyString());
        }

        @Test
        @DisplayName("deletes orphaned files not referenced by any DB record")
        void deletesOrphanedFiles() {
            when(minioStorageService.listAllObjectKeys())
                    .thenReturn(List.of("key-1.xml", "key-2.xml", "key-3.xml"));
            when(documentRepository.findAll(any(Pageable.class)))
                    .thenReturn(Page.empty());

            int deleted = scheduler.reconcileMinioFiles();

            assertThat(deleted).isEqualTo(3);
            verify(minioStorageService, times(3)).delete(anyString());
        }

        @Test
        @DisplayName("preserves files referenced by COMPLETED documents")
        void preservesReferencedFiles() {
            SignedXmlDocumentEntity doc = SignedXmlDocumentEntity.builder()
                    .documentId("inv-001")
                    .originalXmlPath("original-key.xml")
                    .signedXmlPath("signed-key.xml")
                    .status(SigningStatus.COMPLETED)
                    .build();

            when(minioStorageService.listAllObjectKeys())
                    .thenReturn(List.of("original-key.xml", "signed-key.xml", "orphan-key.xml"));
            when(documentRepository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(doc)));

            int deleted = scheduler.reconcileMinioFiles();

            assertThat(deleted).isEqualTo(1);
            verify(minioStorageService).delete("orphan-key.xml");
            verify(minioStorageService, never()).delete("original-key.xml");
            verify(minioStorageService, never()).delete("signed-key.xml");
        }

        @Test
        @DisplayName("increments orphaned files deleted counter")
        void incrementsCounter() {
            when(minioStorageService.listAllObjectKeys())
                    .thenReturn(List.of("orphan-1.xml", "orphan-2.xml"));
            when(documentRepository.findAll(any(Pageable.class)))
                    .thenReturn(Page.empty());

            scheduler.reconcileMinioFiles();

            Counter counter = meterRegistry.find("minio.cleanup.orphaned-files-deleted").counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(2.0);
        }
    }

}
