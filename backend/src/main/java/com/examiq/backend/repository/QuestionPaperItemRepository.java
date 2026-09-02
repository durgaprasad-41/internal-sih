package com.examiq.backend.repository;

import com.examiq.backend.entity.QuestionPaper;
import com.examiq.backend.entity.QuestionPaperItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionPaperItemRepository extends JpaRepository<QuestionPaperItem, Long> {
    List<QuestionPaperItem> findByQuestionPaperOrderByOrderIndexAsc(QuestionPaper questionPaper);

    void deleteByQuestionPaper(QuestionPaper questionPaper);
}
