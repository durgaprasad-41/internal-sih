package com.examiq.backend.service;

import com.examiq.backend.entity.Question;
import com.examiq.backend.entity.Rating;
import com.examiq.backend.entity.Subject;
import com.examiq.backend.repository.QuestionRepository;
import com.examiq.backend.repository.RatingRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * Shared question-analysis engine, sitting on top of the same verified
 * (APPROVED-paper) Question/TopicMapping data that QuestionPaperService's
 * bank-search step reads. Both the Faculty Question Paper Generator and the
 * Student Smart Revision feature ultimately depend on "find verified
 * questions for a subject+topic" - this is that shared read path, plus the
 * scoring primitives that are specific to ranking questions by real-world
 * importance rather than just picking any match.
 */
@Service
public class QuestionAnalysisService {

    private final QuestionRepository questionRepository;
    private final RatingRepository ratingRepository;

    public QuestionAnalysisService(QuestionRepository questionRepository, RatingRepository ratingRepository) {
        this.questionRepository = questionRepository;
        this.ratingRepository = ratingRepository;
    }

    public List<Question> findVerifiedQuestions(Subject subject, String topic) {
        return questionRepository.findVerifiedBySubjectAndTopic(subject, topic);
    }

    /**
     * How many *other* verified questions (across any topic search result
     * set) have essentially the same normalized text as this one - a real,
     * data-derived repetition signal (the same question text recurring
     * across multiple uploaded papers), not a fabricated statistic.
     */
    public long repetitionCount(Question question, List<Question> pool) {
        String normalized = normalize(question.getQuestionText());
        return pool.stream().filter(q -> normalize(q.getQuestionText()).equals(normalized)).count();
    }

    /** 0..1, higher = more recent. Based on the question's own createdAt (when it was ingested). */
    public double recencyScore(Question question) {
        if (question.getCreatedAt() == null) {
            return 0.5; // unknown recency - neutral, not fabricated as "old" or "new"
        }
        long daysOld = ChronoUnit.DAYS.between(question.getCreatedAt(), LocalDateTime.now());
        if (daysOld <= 0) {
            return 1.0;
        }
        // Decays toward 0 over ~2 years; never fully zero.
        double score = 1.0 - Math.min(1.0, daysOld / 730.0);
        return Math.max(0.1, score);
    }

    /** Average rating (1-5) of the paper this question was drawn from, as a mild quality signal. Null if unrated. */
    public Double sourcePaperRating(Question question) {
        if (question.getPaper() == null) {
            return null;
        }
        List<Rating> ratings = ratingRepository.findByPaper(question.getPaper());
        if (ratings.isEmpty()) {
            return null;
        }
        return ratings.stream().mapToInt(Rating::getScore).average().orElse(0.0);
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
