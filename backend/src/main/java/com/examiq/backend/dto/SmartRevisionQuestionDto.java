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
}
