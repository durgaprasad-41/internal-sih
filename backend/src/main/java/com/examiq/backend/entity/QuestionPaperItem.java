package com.examiq.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "question_paper_items")
@Getter
@Setter
@NoArgsConstructor
public class QuestionPaperItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_paper_id", nullable = false)
    private QuestionPaper questionPaper;

    // Set only when source = QUESTION_BANK, for traceability back to the
    // verified bank question this item was drawn from. Null for AI_GENERATED
    // items, which have no backing bank record.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private Question question;

    // Always populated regardless of source - a snapshot of the question text
    // as it appeared in this generated paper, so later edits to a bank
    // question don't retroactively change historical generated papers.
    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(nullable = false, length = 30)
    private String source;

    @Column(length = 150)
    private String topic;

    @Column(length = 30)
    private String difficulty;

    @Column
    private Integer marks;

    @Column(name = "order_index")
    private Integer orderIndex;
}
