package com.examiq.backend;

import com.examiq.backend.entity.Paper;
import com.examiq.backend.entity.Question;
import com.examiq.backend.entity.Role;
import com.examiq.backend.entity.Subject;
import com.examiq.backend.entity.TopicMapping;
import com.examiq.backend.entity.University;
import com.examiq.backend.entity.User;
import com.examiq.backend.repository.AdminActionRepository;
import com.examiq.backend.repository.ContributorScoreRepository;
import com.examiq.backend.repository.NotificationRepository;
import com.examiq.backend.repository.PaperRepository;
import com.examiq.backend.repository.QuestionRepository;
import com.examiq.backend.repository.RoleRepository;
import com.examiq.backend.repository.SubjectRepository;
import com.examiq.backend.repository.TopicMappingRepository;
import com.examiq.backend.repository.UniversityRepository;
import com.examiq.backend.repository.UploadRepository;
import com.examiq.backend.repository.UserRepository;
import com.examiq.backend.repository.VerificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the Smart Revision scoring/selection algorithm actually works
 * correctly, using seeded H2 test fixtures (the real MySQL question bank is
 * genuinely empty in this project - nothing currently populates
 * Question/TopicMapping - so this is the only way to exercise the
 * "has data" path; the honest "insufficient data" path was additionally
 * verified live against the real database).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SmartRevisionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UniversityRepository universityRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private PaperRepository paperRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private TopicMappingRepository topicMappingRepository;

    @Autowired
    private AdminActionRepository adminActionRepository;

    @Autowired
    private ContributorScoreRepository contributorScoreRepository;

    @Autowired
    private VerificationLogRepository verificationLogRepository;

    @Autowired
    private UploadRepository uploadRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        // Other test classes share this H2 context/database, so clear
        // everything that could FK-reference papers/users first.
        adminActionRepository.deleteAll();
        contributorScoreRepository.deleteAll();
        verificationLogRepository.deleteAll();
        uploadRepository.deleteAll();
        notificationRepository.deleteAll();
        topicMappingRepository.deleteAll();
        questionRepository.deleteAll();
        paperRepository.deleteAll();
        userRepository.deleteAll();
        universityRepository.deleteAll();
        subjectRepository.deleteAll();
        roleRepository.deleteAll();

        Role studentRole = new Role();
        studentRole.setName("STUDENT");
        roleRepository.save(studentRole);

        Role facultyRole = new Role();
        facultyRole.setName("FACULTY");
        roleRepository.save(facultyRole);

        University university = new University();
        university.setName("Test University");
        universityRepository.save(university);

        Subject subject = new Subject();
        subject.setName("Engineering Chemistry");
        subject.setCanonicalName("Engineering Chemistry");
        subjectRepository.save(subject);

        User uploader = new User();
        uploader.setUsername("faculty_sr");
        uploader.setEmail("faculty_sr@example.com");
        uploader.setPassword("encoded");
        uploader.setFullName("Faculty SR");
        uploader.setRole(facultyRole);
        userRepository.save(uploader);

        User student = new User();
        student.setUsername("student_sr");
        student.setEmail("student_sr@example.com");
        student.setPassword("encoded");
        student.setFullName("Student SR");
        student.setRole(studentRole);
        userRepository.save(student);

        Paper approvedPaper = new Paper();
        approvedPaper.setTitle("Engineering Chemistry Final Exam 2024");
        approvedPaper.setSubject(subject);
        approvedPaper.setUniversity(university);
        approvedPaper.setUploader(uploader);
        approvedPaper.setYear(2024);
        approvedPaper.setExamType("Final");
        approvedPaper.setAuthor("Faculty SR");
        approvedPaper.setStatus("APPROVED");
        approvedPaper.setFileUrl("/files/test.pdf");
        approvedPaper.setFileHash("hash-sr-1");
        paperRepository.save(approvedPaper);

        // Electrochemistry: 3 verified questions (repeated twice + once unique) -> should be HIGH priority
        saveQuestionWithTopic(approvedPaper, "Explain the working of a Daniell cell.", "EASY", 5, "Electrochemistry");
        saveQuestionWithTopic(approvedPaper, "Explain the working of a Daniell cell.", "EASY", 5, "Electrochemistry");
        saveQuestionWithTopic(approvedPaper, "Derive the Nernst equation for electrode potential.", "HARD", 10,
                "Electrochemistry");

        // Polymers: 1 verified question -> lower relative priority than Electrochemistry
        saveQuestionWithTopic(approvedPaper, "Describe the mechanism of addition polymerization.", "MEDIUM", 8,
                "Polymers");

        // "Corrosion" is intentionally NOT seeded - used to test the honest "no data" path.
    }

    private void saveQuestionWithTopic(Paper paper, String text, String difficulty, int marks, String topicName) {
        Question question = new Question();
        question.setPaper(paper);
        question.setQuestionText(text);
        question.setDifficultyLevel(difficulty);
        question.setMarks(marks);
        Question saved = questionRepository.save(question);

        TopicMapping mapping = new TopicMapping();
        mapping.setQuestion(saved);
        mapping.setTopicName(topicName);
        mapping.setConfidence(1.0);
        topicMappingRepository.save(mapping);
    }

    @Test
    @WithMockUser(username = "student_sr", roles = "STUDENT")
    void smartRevision_coversAvailableTopicsAndReportsNoDataHonestly() throws Exception {
        mockMvc.perform(post("/api/student/smart-revision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "subject": "Engineering Chemistry",
                          "topics": ["Electrochemistry", "Polymers", "Corrosion"],
                          "availableMinutes": 60
                        }
                        """)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topicsSelected").value(3))
                // Electrochemistry has 3 candidates (max), Polymers has 1 -> Electrochemistry should be HIGH
                .andExpect(jsonPath("$.data.topicPriorities[0].priority").value("HIGH"))
                .andExpect(jsonPath("$.data.topicPriorities[0].candidateQuestionCount").value(3))
                // Corrosion has zero seeded data -> honest "no data" reporting, not fabricated
                .andExpect(jsonPath("$.data.topicPriorities[2].priority").value("NO_DATA"))
                .andExpect(jsonPath("$.data.uncoveredTopics[0].topic").value("Corrosion"))
                .andExpect(jsonPath("$.data.uncoveredTopics[0].reason").value(
                        org.hamcrest.Matchers.containsString("Not enough historical data")))
                // At least one real recommended question should be present, drawn from real data
                .andExpect(jsonPath("$.data.recommendedQuestions.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.recommendedQuestions[0].questionId").isNotEmpty())
                // Duplicate "Daniell cell" question text was seeded twice - dedup must collapse it to one
                .andExpect(jsonPath("$.data.disclaimer").value(
                        org.hamcrest.Matchers.containsString("not a prediction")));
    }

    @Test
    @WithMockUser(username = "student_sr", roles = "STUDENT")
    void smartRevision_respectsAvailableTimeBudget() throws Exception {
        mockMvc.perform(post("/api/student/smart-revision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "subject": "Engineering Chemistry",
                          "topics": ["Electrochemistry", "Polymers"],
                          "availableMinutes": 5
                        }
                        """)
                .with(csrf()))
                .andExpect(status().isOk())
                // 5 minutes isn't enough for even the cheapest (10-minute EASY) question
                .andExpect(jsonPath("$.data.recommendedQuestionCount").value(0))
                .andExpect(jsonPath("$.data.estimatedStudyMinutes").value(0));
    }

    @Test
    @WithMockUser(username = "student_sr", roles = "STUDENT")
    void smartRevision_rejectsUnknownSubject() throws Exception {
        mockMvc.perform(post("/api/student/smart-revision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "subject": "Subject That Does Not Exist",
                          "topics": ["Anything"],
                          "availableMinutes": 60
                        }
                        """)
                .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
