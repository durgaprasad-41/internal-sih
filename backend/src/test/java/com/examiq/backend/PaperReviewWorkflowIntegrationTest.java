package com.examiq.backend;

import com.examiq.backend.entity.Notification;
import com.examiq.backend.entity.Role;
import com.examiq.backend.entity.Subject;
import com.examiq.backend.entity.University;
import com.examiq.backend.entity.User;
import com.examiq.backend.repository.AdminActionRepository;
import com.examiq.backend.repository.ContributorScoreRepository;
import com.examiq.backend.repository.NotificationRepository;
import com.examiq.backend.repository.PaperRepository;
import com.examiq.backend.repository.RoleRepository;
import com.examiq.backend.repository.SubjectRepository;
import com.examiq.backend.repository.UniversityRepository;
import com.examiq.backend.repository.UploadRepository;
import com.examiq.backend.repository.UserRepository;
import com.examiq.backend.repository.VerificationLogRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the confidence-based upload review workflow: automatic acceptance,
 * uncertain papers routed to admin review (with an admin notification),
 * automatic rejection of clear mismatches, and admin accept/reject
 * (including the resulting uploader notification). No AI service is mocked
 * here - app.ai.service-url stays at its unreachable default, deliberately
 * exercising the exact real-world condition (ai-service down) that caused
 * the original bug: the system must still make a correct decision from
 * content alone.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PaperReviewWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaperRepository paperRepository;

    @Autowired
    private UniversityRepository universityRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private UploadRepository uploadRepository;

    @Autowired
    private VerificationLogRepository verificationLogRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AdminActionRepository adminActionRepository;

    @Autowired
    private ContributorScoreRepository contributorScoreRepository;

    @BeforeEach
    void setUp() {
        adminActionRepository.deleteAll();
        contributorScoreRepository.deleteAll();
        verificationLogRepository.deleteAll();
        uploadRepository.deleteAll();
        paperRepository.deleteAll();
        notificationRepository.deleteAll();
        userRepository.deleteAll();
        universityRepository.deleteAll();
        subjectRepository.deleteAll();
        roleRepository.deleteAll();

        Role studentRole = new Role();
        studentRole.setName("STUDENT");
        roleRepository.save(studentRole);

        Role adminRole = new Role();
        adminRole.setName("ADMIN");
        roleRepository.save(adminRole);

        University university = new University();
        university.setName("NIT Trichy");
        universityRepository.save(university);

        Subject chemistry = new Subject();
        chemistry.setName("Engineering Chemistry");
        chemistry.setCanonicalName("Engineering Chemistry");
        subjectRepository.save(chemistry);

        Subject operatingSystems = new Subject();
        operatingSystems.setName("Operating Systems");
        operatingSystems.setCanonicalName("Operating Systems");
        subjectRepository.save(operatingSystems);

        User student = new User();
        student.setUsername("student1");
        student.setEmail("student1@example.com");
        student.setPassword("encoded");
        student.setFullName("Student One");
        student.setRole(studentRole);
        student.setUniversity(university);
        userRepository.save(student);

        User admin = new User();
        admin.setUsername("admin1");
        admin.setEmail("admin1@example.com");
        admin.setPassword("encoded");
        admin.setFullName("Admin One");
        admin.setRole(adminRole);
        userRepository.save(admin);
    }

    private MockMultipartFile pdfFile() {
        return new MockMultipartFile("file", "sample.pdf", MediaType.APPLICATION_PDF_VALUE,
                "test pdf content".getBytes());
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    void clearlyCorrectPaper_isAutomaticallyAccepted() throws Exception {
        mockMvc.perform(multipart("/api/papers/upload")
                .file(pdfFile())
                .param("title", "Engineering Chemistry Question Paper Final Exam")
                .param("subject", "Engineering Chemistry")
                .param("university", "NIT Trichy")
                .param("year", "2024")
                .param("examType", "Final")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    void uncertainPaper_isSentToPendingReview_andNotifiesAdmins() throws Exception {
        mockMvc.perform(multipart("/api/papers/upload")
                .file(pdfFile())
                .param("title", "Sample Question Paper Final Exam 2024")
                .param("subject", "Engineering Chemistry")
                .param("university", "NIT Trichy")
                .param("year", "2024")
                .param("examType", "Final")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        User admin = userRepository.findByUsername("admin1").orElseThrow();
        List<Notification> adminNotifications = notificationRepository.findByUserOrderByCreatedAtDesc(admin);
        assertThat(adminNotifications).isNotEmpty();
        Notification reviewNotification = adminNotifications.get(0);
        assertThat(reviewNotification.getType()).isEqualTo("PAPER_REVIEW_REQUIRED");
        assertThat(reviewNotification.getMessage()).contains("Sample Question Paper Final Exam 2024");
        assertThat(reviewNotification.getMessage()).contains("student1");
        assertThat(reviewNotification.getMessage()).contains("Engineering Chemistry");
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    void clearlyIncorrectPaper_isAutomaticallyRejected_withReason() throws Exception {
        mockMvc.perform(multipart("/api/papers/upload")
                .file(pdfFile())
                .param("title", "Operating Systems Final Exam")
                .param("subject", "Engineering Chemistry")
                .param("university", "NIT Trichy")
                .param("year", "2024")
                .param("examType", "Final")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void adminCanAcceptAPendingPaper_andUploaderIsNotified() throws Exception {
        Long paperId = uploadUncertainPaperAsStudent();

        mockMvc.perform(put("/api/admin/papers/{paperId}/approve", paperId)
                .param("reason", "Looks correct on manual review")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        User student = userRepository.findByUsername("student1").orElseThrow();
        List<Notification> studentNotifications = notificationRepository.findByUserOrderByCreatedAtDesc(student);
        assertThat(studentNotifications).anyMatch(n -> "PAPER_APPROVED".equals(n.getType()));
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void adminCanRejectAPendingPaper_andUploaderIsNotifiedWithReason() throws Exception {
        Long paperId = uploadUncertainPaperAsStudent();

        mockMvc.perform(put("/api/admin/papers/{paperId}/reject", paperId)
                .param("reason", "Does not match the selected subject after manual review")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        User student = userRepository.findByUsername("student1").orElseThrow();
        List<Notification> studentNotifications = notificationRepository.findByUserOrderByCreatedAtDesc(student);
        assertThat(studentNotifications).anyMatch(n -> "PAPER_REJECTED".equals(n.getType())
                && n.getMessage().contains("Does not match the selected subject after manual review"));
    }

    /**
     * Uploads a paper that the confidence engine cannot confidently place
     * (so it lands as PENDING, awaiting admin review), as a single request
     * authenticated as the student regardless of the calling test's own
     * @WithMockUser, and returns the created paper id.
     */
    private Long uploadUncertainPaperAsStudent() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/papers/upload")
                .file(pdfFile())
                .param("title", "Sample Question Paper Final Exam 2024")
                .param("subject", "Engineering Chemistry")
                .param("university", "NIT Trichy")
                .param("year", "2024")
                .param("examType", "Final")
                .with(user("student1").roles("STUDENT"))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }
}
