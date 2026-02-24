package com.wpanther.xmlsigning.infrastructure.persistence;

import com.wpanther.xmlsigning.domain.model.SignedXmlDocument;
import com.wpanther.xmlsigning.domain.model.SignedXmlDocumentId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

/**
 * MapStruct mapper for SignedXmlDocument domain model and JPA entity
 */
@Mapper(componentModel = "spring")
@Component
public interface SignedXmlDocumentMapper {

    @Mapping(target = "id", expression = "java(new com.wpanther.xmlsigning.domain.model.SignedXmlDocumentId(entity.getId()))")
    @Mapping(source = "invoiceId", target = "invoiceId")
    @Mapping(source = "invoiceNumber", target = "invoiceNumber")
    @Mapping(source = "documentType", target = "documentType")
    @Mapping(source = "originalXmlPath", target = "originalXmlPath")
    @Mapping(source = "originalXmlUrl", target = "originalXmlUrl")
    @Mapping(source = "signedXmlPath", target = "signedXmlPath")
    @Mapping(source = "signedXmlUrl", target = "signedXmlUrl")
    @Mapping(source = "signedXmlSize", target = "signedXmlSize")
    @Mapping(source = "transactionId", target = "transactionId")
    @Mapping(source = "certificate", target = "certificate")
    @Mapping(source = "signatureLevel", target = "signatureLevel")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "errorMessage", target = "errorMessage")
    @Mapping(source = "retryCount", target = "retryCount")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "completedAt", target = "completedAt")
    SignedXmlDocument toDomain(SignedXmlDocumentEntity entity);

    @Mapping(target = "id", expression = "java(domain.getId().value())")
    @Mapping(source = "invoiceId", target = "invoiceId")
    @Mapping(source = "invoiceNumber", target = "invoiceNumber")
    @Mapping(source = "documentType", target = "documentType")
    @Mapping(source = "originalXmlPath", target = "originalXmlPath")
    @Mapping(source = "originalXmlUrl", target = "originalXmlUrl")
    @Mapping(source = "signedXmlPath", target = "signedXmlPath")
    @Mapping(source = "signedXmlUrl", target = "signedXmlUrl")
    @Mapping(source = "signedXmlSize", target = "signedXmlSize")
    @Mapping(source = "transactionId", target = "transactionId")
    @Mapping(source = "certificate", target = "certificate")
    @Mapping(source = "signatureLevel", target = "signatureLevel")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "errorMessage", target = "errorMessage")
    @Mapping(source = "retryCount", target = "retryCount")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "completedAt", target = "completedAt")
    @Mapping(target = "updatedAt", ignore = true)
    SignedXmlDocumentEntity toEntity(SignedXmlDocument domain);
}
