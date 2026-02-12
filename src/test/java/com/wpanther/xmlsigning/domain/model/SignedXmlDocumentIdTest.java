package com.wpanther.xmlsigning.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SignedXmlDocumentId} value object.
 * Tests record factory methods and validation.
 */
@DisplayName("SignedXmlDocumentId Record")
class SignedXmlDocumentIdTest {

    @Nested
    @DisplayName("create() Factory Method")
    class CreateMethod {

        @Test
        @DisplayName("create() generates non-null UUID value object")
        void testCreate() {
            SignedXmlDocumentId id = SignedXmlDocumentId.create();

            assertThat(id).isNotNull();
            assertThat(id.value()).isNotNull();
        }

        @Test
        @DisplayName("Multiple calls generate unique IDs")
        void testCreateUniqueness() {
            SignedXmlDocumentId id1 = SignedXmlDocumentId.create();
            SignedXmlDocumentId id2 = SignedXmlDocumentId.create();

            assertThat(id1).isNotEqualTo(id2);
        }
    }

    @Nested
    @DisplayName("from() Factory Method")
    class FromMethod {

        @Test
        @DisplayName("Valid UUID string returns correct ID")
        void testFrom() {
            UUID uuid = UUID.randomUUID();
            String uuidString = uuid.toString();

            SignedXmlDocumentId result = SignedXmlDocumentId.from(uuidString);

            assertThat(result.value()).isEqualTo(uuid);
            assertThat(result.toString()).isEqualTo(uuidString);
        }

        @Test
        @DisplayName("Invalid format throws IllegalArgumentException")
        void testFromInvalid() {
            String invalidId = "not-a-uuid";

            assertThatThrownBy(() -> SignedXmlDocumentId.from(invalidId))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid document ID format");
        }

        @Test
        @DisplayName("Null input throws NullPointerException")
        void testFromNull() {
            assertThatThrownBy(() -> SignedXmlDocumentId.from(null))
                    .isExactlyInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidation {

        @Test
        @DisplayName("Constructor with null value throws NullPointerException")
        void testConstructorNullValue() {
            assertThatThrownBy(() -> new SignedXmlDocumentId(null))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Document ID cannot be null");
        }
    }

    @Nested
    @DisplayName("toString() Method")
    class ToStringMethod {

        @Test
        @DisplayName("toString() returns UUID string representation")
        void testToString() {
            UUID uuid = UUID.randomUUID();

            SignedXmlDocumentId id = new SignedXmlDocumentId(uuid);

            assertThat(id.toString()).isEqualTo(uuid.toString());
        }
    }

    @Nested
    @DisplayName("equals() and hashCode() - Record Implementation")
    class EqualityAndHashCode {

        @Test
        @DisplayName("Two IDs with same UUID are equal")
        void testEquality() {
            UUID uuid = UUID.randomUUID();

            SignedXmlDocumentId id1 = new SignedXmlDocumentId(uuid);
            SignedXmlDocumentId id2 = new SignedXmlDocumentId(uuid);

            assertThat(id1).isEqualTo(id2);
            assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        }

        @Test
        @DisplayName("Two different IDs are not equal")
        void testInequality() {
            UUID uuid1 = UUID.randomUUID();
            UUID uuid2 = UUID.randomUUID();

            SignedXmlDocumentId id1 = new SignedXmlDocumentId(uuid1);
            SignedXmlDocumentId id2 = new SignedXmlDocumentId(uuid2);

            assertThat(id1).isNotEqualTo(id2);
        }
    }
}
