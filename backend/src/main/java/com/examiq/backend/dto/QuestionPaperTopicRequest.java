package com.examiq.backend.dto;

import lombok.Data;

@Data
public class QuestionPaperTopicRequest {
    private String topicName;
    private Integer numberOfQuestions;
}
