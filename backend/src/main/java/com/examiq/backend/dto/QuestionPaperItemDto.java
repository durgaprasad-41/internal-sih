package com.examiq.backend.dto;

import lombok.Data;

@Data
public class QuestionPaperItemDto {
    private Long id;
    private Long questionId;
    private String questionText;
    private String source;
    private String topic;
    private String difficulty;
    private Integer marks;
    private Integer orderIndex;
}
