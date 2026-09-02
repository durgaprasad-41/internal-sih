package com.examiq.backend.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class QuestionPaperDto {
    private Long id;
    private String collegeName;
    private String examName;
    private String subjectName;
    private LocalDate examDate;
    private Integer totalMarks;
    private Integer totalQuestions;
    private String instructions;
    private Integer easyPercent;
    private Integer mediumPercent;
    private Integer hardPercent;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<QuestionPaperItemDto> items;
}
