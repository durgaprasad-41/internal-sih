package com.examiq.backend;

import com.examiq.backend.service.AdminOtpService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit test - no Spring context needed for this isolated in-memory service. */
class AdminOtpServiceTest {

    @Test
    void generatedCodeIsSixDigits() {
        AdminOtpService service = new AdminOtpService();
        String code = service.generateAndStore("admin1");
        assertThat(code).hasSize(6);
        assertThat(code).matches("\\d{6}");
    }

    @Test
    void correctCodeVerifiesSuccessfully() {
        AdminOtpService service = new AdminOtpService();
        String code = service.generateAndStore("admin1");
        assertThat(service.verify("admin1", code)).isTrue();
    }

    @Test
    void codeIsSingleUse() {
        AdminOtpService service = new AdminOtpService();
        String code = service.generateAndStore("admin1");
        assertThat(service.verify("admin1", code)).isTrue();
        // Second attempt with the same (already-consumed) code must fail.
        assertThat(service.verify("admin1", code)).isFalse();
    }

    @Test
    void wrongCodeIsRejected() {
        AdminOtpService service = new AdminOtpService();
        service.generateAndStore("admin1");
        assertThat(service.verify("admin1", "000000")).isFalse();
    }

    @Test
    void tooManyWrongAttemptsInvalidatesTheCode() {
        AdminOtpService service = new AdminOtpService();
        String code = service.generateAndStore("admin1");
        for (int i = 0; i < 5; i++) {
            assertThat(service.verify("admin1", "999999")).isFalse();
        }
        // Even the originally-correct code is now rejected - the OTP was invalidated after 5 wrong attempts.
        assertThat(service.verify("admin1", code)).isFalse();
    }

    @Test
    void differentUsernamesDoNotInterfere() {
        AdminOtpService service = new AdminOtpService();
        String codeA = service.generateAndStore("admin1");
        String codeB = service.generateAndStore("admin2");
        assertThat(codeA).isNotEqualTo(codeB);
        assertThat(service.verify("admin2", codeA)).isFalse();
        assertThat(service.verify("admin1", codeA)).isTrue();
    }

    @Test
    void verifyingWithNoPriorCodeFails() {
        AdminOtpService service = new AdminOtpService();
        assertThat(service.verify("neverloggedin", "123456")).isFalse();
    }
}
