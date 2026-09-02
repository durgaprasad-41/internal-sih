package com.examiq.backend.repository;

import com.examiq.backend.entity.Paper;
import com.examiq.backend.entity.Question;
import com.examiq.backend.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByPaper(Paper paper);

    /**
     * Candidate questions for the paper generator: drawn only from verified
     * (APPROVED) papers for the given subject, matching topic (via
     * TopicMapping) and difficulty, excluding any already selected for the
     * current generation run.
     */
    @Query("SELECT DISTINCT q FROM Question q JOIN TopicMapping tm ON tm.question = q "
            + "WHERE q.paper.subject = :subject "
            + "AND q.paper.status = 'APPROVED' "
            + "AND LOWER(tm.topicName) = LOWER(:topic) "
            + "AND LOWER(q.difficultyLevel) = LOWER(:difficulty) "
            + "AND q.id NOT IN :excludedIds")
    List<Question> findVerifiedCandidates(@Param("subject") Subject subject, @Param("topic") String topic,
            @Param("difficulty") String difficulty, @Param("excludedIds") List<Long> excludedIds);

    /**
     * Broader than findVerifiedCandidates: every verified question for a
     * subject+topic regardless of difficulty, used by analysis/scoring
     * features (Smart Revision) that need to compare across the full
     * candidate set rather than pull one difficulty tier at a time.
     */
    @Query("SELECT DISTINCT q FROM Question q JOIN TopicMapping tm ON tm.question = q "
            + "WHERE q.paper.subject = :subject "
            + "AND q.paper.status = 'APPROVED' "
            + "AND LOWER(tm.topicName) = LOWER(:topic)")
    List<Question> findVerifiedBySubjectAndTopic(@Param("subject") Subject subject, @Param("topic") String topic);
}
