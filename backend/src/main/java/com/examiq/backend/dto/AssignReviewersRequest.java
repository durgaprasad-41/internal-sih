package com.examiq.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AssignReviewersRequest {
    @NotEmpty(message = "Select at least one faculty reviewer")
    private List<Long> reviewerIds;
}
