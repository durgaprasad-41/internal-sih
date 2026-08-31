package com.examiq.backend;

import com.examiq.backend.entity.Role;
import com.examiq.backend.entity.Subject;
import com.examiq.backend.entity.University;
import com.examiq.backend.entity.User;
import com.examiq.backend.repository.PaperRepository;
import com.examiq.backend.repository.RoleRepository;
import com.examiq.backend.repository.SubjectRepository;
import com.examiq.backend.repository.UniversityRepository;
import com.examiq.backend.repository.UserRepository;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PaperControllerIntegrationTest {

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

    @BeforeEach
    void setUp() {
        // Delete relations in FK order to avoid integrity violations in H2.
        paperRepository.deleteAll();
        userRepository.deleteAll();
        universityRepository.deleteAll();
        subjectRepository.deleteAll();
        roleRepository.deleteAll();

        Role role = new Role();
        role.setName("FACULTY");
        roleRepository.save(role);

        University university = new University();
        university.setName("NIT Trichy");
        universityRepository.save(university);

        Subject subject = new Subject();
        subject.setName("DBMS");
        subject.setCanonicalName("DBMS");
        subjectRepository.save(subject);

        User user = new User();
        user.setUsername("faculty1");
        user.setEmail("faculty1@example.com");
        user.setPassword("encoded");
        user.setFullName("Faculty One");
        user.setRole(role);
        user.setUniversity(university);
        userRepository.save(user);
    }

    @Test
    @WithMockUser(username = "faculty1", roles = "FACULTY")
    void uploadPaper_shouldReturnOk() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "test pdf content".getBytes());

        mockMvc.perform(multipart("/api/papers/upload")
                .file(file)
                .param("title", "DBMS Final Exam")
                .param("subject", "DBMS")
                .param("university", "NIT Trichy")
                .param("year", "2024")
                .param("examType", "Final")
                .param("author", "Faculty One")
                .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "faculty1", roles = "FACULTY")
    void uploadPaper_shouldStoreWebAccessibleFileUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "test pdf content".getBytes());

        mockMvc.perform(multipart("/api/papers/upload")
                .file(file)
                .param("title", "DBMS Final Exam")
                .param("subject", "DBMS")
                .param("university", "NIT Trichy")
                .param("year", "2024")
                .param("examType", "Final")
                .param("author", "Faculty One")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileUrl").value(org.hamcrest.Matchers.startsWith("/files/")));
    }

    @Test
    @WithMockUser(username = "faculty1", roles = "FACULTY")
    void searchPapers_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/papers/search")
                .param("q", "DBMS"))
                .andExpect(status().isOk());
    }
}
