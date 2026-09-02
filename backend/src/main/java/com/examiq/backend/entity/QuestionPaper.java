package com.examiq.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "question_papers")
@Getter
@Setter
@NoArgsConstructor
public class QuestionPaper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id", nullable = false)
    private User faculty;

    @Column(name = "college_name", nullable = false)
    private String collegeName;

    @Column(name = "exam_name", nullable = false)
    private String examName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "exam_date")
    private LocalDate examDate;

    @Column(name = "total_marks")
    private Integer totalMarks;

    @Column(name = "total_questions")
    private Integer totalQuestions;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "easy_percent")
    private Integer easyPercent;

    @Column(name = "medium_percent")
    private Integer mediumPercent;

    @Column(name = "hard_percent")
    private Integer hardPercent;

    @Column(length = 30)
    private String status = "DRAFT";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
