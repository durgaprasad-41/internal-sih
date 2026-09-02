package com.examiq.backend.repository;

import com.examiq.backend.entity.QuestionPaper;
import com.examiq.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionPaperRepository extends JpaRepository<QuestionPaper, Long> {
    List<QuestionPaper> findByFacultyOrderByCreatedAtDesc(User faculty);

    Optional<QuestionPaper> findByIdAndFaculty(Long id, User faculty);
}
