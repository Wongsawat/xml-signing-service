package com.wpanther.xmlsigning.application.dto.csc;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CscDomainValueObjectsTest {

    @Test
    void cscAuthorizeCommand_storesAllFields() {
        var cmd = new CscAuthorizeCommand(
            "cred-1", "2.16.840.1.101.3.4.2.1",
            List.of("abc123"), "1234", "Thai e-Tax signing");
        assertThat(cmd.credentialId()).isEqualTo("cred-1");
        assertThat(cmd.hashAlgorithmOid()).isEqualTo("2.16.840.1.101.3.4.2.1");
        assertThat(cmd.hashes()).containsExactly("abc123");
        assertThat(cmd.pin()).isEqualTo("1234");
        assertThat(cmd.description()).isEqualTo("Thai e-Tax signing");
    }

    @Test
    void cscAuthorizeCommand_acceptsNullPin() {
        var cmd = new CscAuthorizeCommand(
            "cred-1", "2.16.840.1.101.3.4.2.1",
            List.of("abc123"), null, "description");
        assertThat(cmd.pin()).isNull();
    }

    @Test
    void cscAuthorizeResult_storesSadToken() {
        var result = new CscAuthorizeResult("sad-token-xyz");
        assertThat(result.sadToken()).isEqualTo("sad-token-xyz");
    }

    @Test
    void cscSignHashCommand_storesAllFields() {
        var cmd = new CscSignHashCommand(
            "cred-1", "sad-token", "2.16.840.1.101.3.4.2.1",
            List.of("digest1"));
        assertThat(cmd.credentialId()).isEqualTo("cred-1");
        assertThat(cmd.sadToken()).isEqualTo("sad-token");
        assertThat(cmd.hashAlgorithmOid()).isEqualTo("2.16.840.1.101.3.4.2.1");
        assertThat(cmd.hashes()).containsExactly("digest1");
    }

    @Test
    void cscSignHashResult_storesSignaturesAndResponseId() {
        var result = new CscSignHashResult(List.of("sig1"), "resp-id-001");
        assertThat(result.signatures()).containsExactly("sig1");
        assertThat(result.responseId()).isEqualTo("resp-id-001");
    }
}