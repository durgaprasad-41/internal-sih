package com.examiq.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class SmartRevisionRequest {

    @NotBlank
    private String subject;

    @NotEmpty
    private List<String> topics;

    @NotNull
    @Positive
    private Integer availableMinutes;

    private Integer targetMarks;

    private LocalDate examDate;
}
