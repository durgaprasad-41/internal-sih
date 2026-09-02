package com.examiq.backend;

import com.examiq.backend.entity.Notification;
import com.examiq.backend.entity.Role;
import com.examiq.backend.entity.Subject;
import com.examiq.backend.entity.University;
import com.examiq.backend.entity.User;
import com.examiq.backend.repository.AdminActionRepository;
import com.examiq.backend.repository.ContributorScoreRepository;
import com.examiq.backend.repository.NotificationRepository;
import com.examiq.backend.repository.PaperReviewAssignmentRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the admin -> faculty peer review -> admin final decision workflow:
 * an admin forwards a PENDING paper to faculty reviewers, each reviewer's
 * accept/reject is advisory only, and only once every assigned reviewer has
 * responded does the admin get a recommendation notification - the paper
 * itself never leaves UNDER_REVIEW until the admin makes the final call.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PaperPeerReviewIntegrationTest {

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

    @Autowired
    private PaperReviewAssignmentRepository paperReviewAssignmentRepository;

    private Long facultyOneId;
    private Long facultyTwoId;

    @BeforeEach
    void setUp() {
        paperReviewAssignmentRepository.deleteAll();
        adminActionRepository.deleteAll();
        contributorScoreRepository.deleteAll();
        verificationLogRepository.deleteAll();
        uploadRepository.deleteAll();
        paperRepository.deleteAll();
        notificationRepository.deleteAll();
        userRepository.deleteAll();
        universityRepository.deleteAll();
        subjectRepository.deleteAll();

        Role studentRole = roleRepository.findByName("STUDENT").orElseGet(() -> {
            Role r = new Role();
            r.setName("STUDENT");
            return roleRepository.save(r);
        });
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> {
            Role r = new Role();
            r.setName("ADMIN");
            return roleRepository.save(r);
        });
        Role facultyRole = roleRepository.findByName("FACULTY").orElseGet(() -> {
            Role r = new Role();
            r.setName("FACULTY");
            return roleRepository.save(r);
        });

        University university = new University();
        university.setName("NIT Trichy");
        universityRepository.save(university);

        Subject chemistry = new Subject();
        chemistry.setName("Engineering Chemistry");
        chemistry.setCanonicalName("Engineering Chemistry");
        subjectRepository.save(chemistry);

        User student = new User();
        student.setUsername("peerstudent1");
        student.setEmail("peerstudent1@example.com");
        student.setPassword("encoded");
        student.setFullName("Peer Student One");
        student.setRole(studentRole);
        student.setUniversity(university);
        userRepository.save(student);

        User admin = new User();
        admin.setUsername("peeradmin1");
        admin.setEmail("peeradmin1@example.com");
        admin.setPassword("encoded");
        admin.setFullName("Peer Admin One");
        admin.setRole(adminRole);
        userRepository.save(admin);

        User facultyOne = new User();
        facultyOne.setUsername("peerfaculty1");
        facultyOne.setEmail("peerfaculty1@example.com");
        facultyOne.setPassword("encoded");
        facultyOne.setFullName("Peer Faculty One");
        facultyOne.setRole(facultyRole);
        userRepository.save(facultyOne);
        facultyOneId = facultyOne.getId();

        User facultyTwo = new User();
        facultyTwo.setUsername("peerfaculty2");
        facultyTwo.setEmail("peerfaculty2@example.com");
        facultyTwo.setPassword("encoded");
        facultyTwo.setFullName("Peer Faculty Two");
        facultyTwo.setRole(facultyRole);
        userRepository.save(facultyTwo);
        facultyTwoId = facultyTwo.getId();
    }

    private MockMultipartFile pdfFile() {
        return new MockMultipartFile("file", "sample.pdf", MediaType.APPLICATION_PDF_VALUE,
                "test pdf content".getBytes());
    }

    private Long uploadPendingPaperAsStudent() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/papers/upload")
                .file(pdfFile())
                .param("title", "Engineering Chemistry Question Paper Final Exam")
                .param("subject", "Engineering Chemistry")
                .param("university", "NIT Trichy")
                .param("year", "2024")
                .param("examType", "Final")
                .with(user("peerstudent1").roles("STUDENT"))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        Integer id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    @Test
    @WithMockUser(username = "peeradmin1", roles = "ADMIN")
    void adminCanForwardAPendingPaperToFacultyReviewers_andBothAreNotified() throws Exception {
        Long paperId = uploadPendingPaperAsStudent();

        mockMvc.perform(post("/api/admin/papers/{paperId}/assign-reviewers", paperId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewerIds\":[" + facultyOneId + "," + facultyTwoId + "]}")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paper.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.data.totalAssigned").value(2))
                .andExpect(jsonPath("$.data.reviewComplete").value(false));

        User facultyOne = userRepository.findById(facultyOneId).orElseThrow();
        User facultyTwo = userRepository.findById(facultyTwoId).orElseThrow();
        assertThat(notificationRepository.findByUserOrderByCreatedAtDesc(facultyOne))
                .anyMatch(n -> "REVIEW_REQUESTED".equals(n.getType()));
        assertThat(notificationRepository.findByUserOrderByCreatedAtDesc(facultyTwo))
                .anyMatch(n -> "REVIEW_REQUESTED".equals(n.getType()));
    }

    @Test
    @WithMockUser(username = "peeradmin1", roles = "ADMIN")
    void assigningANonFacultyUserAsReviewer_isRejected() throws Exception {
        Long paperId = uploadPendingPaperAsStudent();
        User student = userRepository.findByUsername("peerstudent1").orElseThrow();

        mockMvc.perform(post("/api/admin/papers/{paperId}/assign-reviewers", paperId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewerIds\":[" + student.getId() + "]}")
                .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fullPeerReviewFlow_recommendsAcceptance_afterBothReviewersRespond_andAdminMakesFinalCall() throws Exception {
        Long paperId = uploadPendingPaperAsStudent();

        mockMvc.perform(post("/api/admin/papers/{paperId}/assign-reviewers", paperId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewerIds\":[" + facultyOneId + "," + facultyTwoId + "]}")
                .with(user("peeradmin1").roles("ADMIN"))
                .with(csrf()))
                .andExpect(status().isOk());

        MvcResult summaryResult = mockMvc.perform(get("/api/admin/papers/{paperId}/review-summary", paperId)
                        .with(user("peeradmin1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn();
        List<Integer> assignmentIds = JsonPath.read(summaryResult.getResponse().getContentAsString(),
                "$.data.assignments[*].id");
        assertThat(assignmentIds).hasSize(2);

        User admin = userRepository.findByUsername("peeradmin1").orElseThrow();
        int adminNotificationsBefore = notificationRepository.findByUserOrderByCreatedAtDesc(admin).size();

        // First reviewer responds - review is not complete yet, so no
        // recommendation notification should be sent to the admin yet.
        mockMvc.perform(put("/api/faculty/reviews/{assignmentId}", assignmentIds.get(0))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"ACCEPT\",\"comment\":\"Looks correct and well-formatted.\"}")
                .with(user("peerfaculty1").roles("FACULTY"))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPT"));

        assertThat(notificationRepository.findByUserOrderByCreatedAtDesc(admin)).hasSize(adminNotificationsBefore);

        // Second reviewer responds - now the review is complete and the
        // admin should get a recommendation notification.
        mockMvc.perform(put("/api/faculty/reviews/{assignmentId}", assignmentIds.get(1))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"ACCEPT\",\"comment\":\"Confirmed, matches the syllabus.\"}")
                .with(user("peerfaculty2").roles("FACULTY"))
                .with(csrf()))
                .andExpect(status().isOk());

        List<Notification> adminNotifications = notificationRepository.findByUserOrderByCreatedAtDesc(admin);
        assertThat(adminNotifications).anyMatch(n -> "PAPER_REVIEW_COMPLETE".equals(n.getType())
                && n.getMessage().contains("2 accept, 0 reject")
                && n.getMessage().contains("acceptance"));

        // The paper itself must still be UNDER_REVIEW - faculty input never
        // changes its status by itself.
        assertThat(paperRepository.findById(paperId).orElseThrow().getStatus()).isEqualTo("UNDER_REVIEW");

        // Admin makes the FINAL decision using the existing approve endpoint.
        mockMvc.perform(put("/api/admin/papers/{paperId}/approve", paperId)
                .param("reason", "Approved after unanimous faculty recommendation")
                .with(user("peeradmin1").roles("ADMIN"))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void facultyMemberNotAssignedToAPaper_cannotSubmitAReviewForIt() throws Exception {
        Long paperId = uploadPendingPaperAsStudent();

        mockMvc.perform(post("/api/admin/papers/{paperId}/assign-reviewers", paperId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewerIds\":[" + facultyOneId + "]}")
                .with(user("peeradmin1").roles("ADMIN"))
                .with(csrf()))
                .andExpect(status().isOk());

        // facultyTwo was never assigned - there is no assignment id for them
        // to act on, so hitting a nonexistent/foreign assignment id must fail.
        mockMvc.perform(put("/api/faculty/reviews/{assignmentId}", 999999)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"ACCEPT\"}")
                .with(user("peerfaculty2").roles("FACULTY"))
                .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
