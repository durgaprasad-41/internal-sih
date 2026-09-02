package com.examiq.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubmitReviewRequest {
    /** ACCEPT or REJECT. */
    @NotBlank(message = "A decision (ACCEPT or REJECT) is required")
    private String decision;

    private String comment;
}
