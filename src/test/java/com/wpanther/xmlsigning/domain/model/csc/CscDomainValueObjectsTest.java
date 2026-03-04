package com.wpanther.xmlsigning.domain.model.csc;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CscDomainValueObjectsTest {

    @Test
    void cscAuthorizeCommand_storesAllFields() {
        var cmd = new CscAuthorizeCommand(
            "client-1", "cred-1", "1", "SHA256",
            new String[]{"abc123"}, "Thai e-Tax signing");
        assertThat(cmd.clientId()).isEqualTo("client-1");
        assertThat(cmd.credentialId()).isEqualTo("cred-1");
        assertThat(cmd.documentDigests()).containsExactly("abc123");
    }

    @Test
    void cscAuthorizeResult_storesAllFields() {
        var result = new CscAuthorizeResult("sad-token-xyz", "txn-001");
        assertThat(result.sadToken()).isEqualTo("sad-token-xyz");
        assertThat(result.transactionId()).isEqualTo("txn-001");
    }

    @Test
    void cscSignHashCommand_storesAllFields() {
        var cmd = new CscSignHashCommand(
            "client-1", "cred-1", "sad-token", "SHA256withRSA",
            new String[]{"digest1"}, "XAdES", "XAdES-BASELINE-T",
            "enveloped", "SHA256", System.currentTimeMillis());
        assertThat(cmd.clientId()).isEqualTo("client-1");
        assertThat(cmd.sadToken()).isEqualTo("sad-token");
    }

    @Test
    void cscSignHashResult_storesAllFields() {
        var result = new CscSignHashResult(new String[]{"sig1"}, "cert-pem");
        assertThat(result.signatures()).containsExactly("sig1");
        assertThat(result.certificate()).isEqualTo("cert-pem");
    }
}
