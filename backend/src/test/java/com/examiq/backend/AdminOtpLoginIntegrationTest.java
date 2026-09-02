package com.examiq.backend;

import com.examiq.backend.entity.Role;
import com.examiq.backend.entity.User;
import com.examiq.backend.repository.RoleRepository;
import com.examiq.backend.repository.UserRepository;
import com.examiq.backend.support.TestEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full admin-login OTP flow, end to end through the real controllers/
 * services, with only the SMTP send itself swapped for a capturing test
 * double (TestEmailService) - no real mail server involved.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminOtpLoginIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TestEmailService testEmailService;

    private static final String ADMIN_EMAIL = "otpadmin@example.com";

    @BeforeEach
    void setUp() {
        // Other test classes share this Spring context/database and rely on
        // all three roles (STUDENT/FACULTY/ADMIN) persisting across the
        // whole suite - never delete roles here, only find-or-create the
        // ones this test needs, and only reset the users this test owns.
        userRepository.deleteAll();

        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> {
            Role role = new Role();
            role.setName("ADMIN");
            return roleRepository.save(role);
        });
        Role studentRole = roleRepository.findByName("STUDENT").orElseGet(() -> {
            Role role = new Role();
            role.setName("STUDENT");
            return roleRepository.save(role);
        });

        User admin = new User();
        admin.setUsername("otpadmin");
        admin.setEmail(ADMIN_EMAIL);
        admin.setPassword(passwordEncoder.encode("AdminPass123"));
        admin.setFullName("OTP Admin");
        admin.setRole(adminRole);
        admin.setStatus("ACTIVE");
        userRepository.save(admin);

        User student = new User();
        student.setUsername("otpstudent");
        student.setEmail("otpstudent@example.com");
        student.setPassword(passwordEncoder.encode("StudentPass123"));
        student.setFullName("OTP Student");
        student.setRole(studentRole);
        student.setStatus("ACTIVE");
        userRepository.save(student);
    }

    @Test
    void adminLogin_requiresOtp_andDoesNotIssueATokenYet() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"otpadmin\",\"password\":\"AdminPass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.otpRequired").value(true))
                .andExpect(jsonPath("$.data.token").doesNotExist());

        assertThat(testEmailService.getLastOtp(ADMIN_EMAIL)).isNotNull();
        assertThat(testEmailService.getLastOtp(ADMIN_EMAIL)).matches("\\d{6}");
    }

    @Test
    void studentLogin_doesNotRequireOtp_tokenIssuedImmediately() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"otpstudent\",\"password\":\"StudentPass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.otpRequired").value(false))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.role").value("STUDENT"));

        assertThat(testEmailService.getLastOtp("otpstudent@example.com")).isNull();
    }

    @Test
    void verifyOtp_withCorrectCode_issuesAdminToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"otpadmin\",\"password\":\"AdminPass123\"}"))
                .andExpect(status().isOk());

        String otp = testEmailService.getLastOtp(ADMIN_EMAIL);
        assertThat(otp).isNotNull();

        mockMvc.perform(post("/api/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"otpadmin\",\"code\":\"" + otp + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.otpRequired").value(false))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void verifyOtp_withWrongCode_isRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"otpadmin\",\"password\":\"AdminPass123\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"otpadmin\",\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyOtp_forNonAdmin_isRejectedEvenWithNoCodeEverSent() throws Exception {
        mockMvc.perform(post("/api/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"otpstudent\",\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongPassword_forAdmin_stillFailsBeforeAnyOtpIsSent() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"otpadmin\",\"password\":\"WrongPassword\"}"))
                .andExpect(status().isUnauthorized());
    }
}
