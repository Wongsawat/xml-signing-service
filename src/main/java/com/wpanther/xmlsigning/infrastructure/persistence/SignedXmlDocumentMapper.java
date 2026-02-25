package com.wpanther.xmlsigning.infrastructure.persistence;

import com.wpanther.xmlsigning.domain.model.SignedXmlDocument;
import com.wpanther.xmlsigning.domain.model.SignedXmlDocumentId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.stereotype.Component;

/**
 * MapStruct mapper for SignedXmlDocument domain model and JPA entity.
 * <p>
 * MapStruct automatically maps fields with matching names. Only fields requiring
 * special handling (ID transformation, ignored fields) need explicit annotations.
 */
@Mapper(componentModel = "spring")
@Component
public interface SignedXmlDocumentMapper {

    /**
     * Maps JPA entity to domain model.
     * <p>
     * The ID field requires transformation from String to SignedXmlDocumentId value object.
     * All other fields with matching names are mapped automatically.
     *
     * @param entity the JPA entity
     * @return the domain model
     */
    @Mapping(target = "id", expression = "java(new com.wpanther.xmlsigning.domain.model.SignedXmlDocumentId(entity.getId()))")
    SignedXmlDocument toDomain(SignedXmlDocumentEntity entity);

    /**
     * Maps domain model to JPA entity.
     * <p>
     * The ID field requires transformation from SignedXmlDocumentId value object to String.
     * The updatedAt field is ignored (managed by JPA/@PreUpdate).
     * All other fields with matching names are mapped automatically.
     *
     * @param domain the domain model
     * @return the JPA entity
     */
    @Mapping(target = "id", expression = "java(domain.getId().value())")
    @Mapping(target = "updatedAt", ignore = true)
    SignedXmlDocumentEntity toEntity(SignedXmlDocument domain);
}
