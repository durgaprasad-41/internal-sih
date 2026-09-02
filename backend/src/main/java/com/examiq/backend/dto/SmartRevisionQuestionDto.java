package com.examiq.backend.dto;

import lombok.Data;

@Data
public class SmartRevisionQuestionDto {
    private Long questionId;
    private String questionText;
    private String topic;
    private Integer marks;
    private String difficulty;
    private Double priorityScore;
    private String priorityCategory;
    private Integer estimatedMinutes;
    private String reason;
    private String sourcePaperTitle;

    /** MUST_STUDY / HIGH_PRIORITY / SYLLABUS_COVERAGE - the study-plan tier this question was placed in. */
    private String tier;

    /** QUESTION_BANK (drawn from a real approved paper) or AI_GENERATED (syllabus-coverage placeholder, no matching past-paper record). */
    private String source;
}
