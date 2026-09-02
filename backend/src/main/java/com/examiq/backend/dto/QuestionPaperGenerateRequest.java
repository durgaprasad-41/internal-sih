package com.examiq.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class QuestionPaperGenerateRequest {

    @NotBlank
    private String collegeName;

    @NotBlank
    private String examName;

    private LocalDate examDate;

    @NotBlank
    private String subject;

    @NotEmpty
    private List<QuestionPaperTopicRequest> topics;

    @NotNull
    private Integer totalMarks;

    // Percentages, expected to sum to ~100; defaulted server-side if omitted.
    private Integer easyPercent;
    private Integer mediumPercent;
    private Integer hardPercent;

    private String instructions;
}
