package com.examiq.backend.dto;

import lombok.Data;

@Data
public class SmartRevisionTopicDto {
    private String topic;
    private String priority; // HIGH / MEDIUM / LOW / NO_DATA
    private String reason;
    private int candidateQuestionCount;
    private int selectedQuestionCount;
    private boolean covered;
}
