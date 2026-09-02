package com.examiq.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaperReviewAssignmentDto {
    private Long id;
    private Long paperId;
    private String paperTitle;
    private String subjectName;
    private Long reviewerId;
    private String reviewerUsername;
    private String reviewerFullName;
    private String status; // PENDING / ACCEPT / REJECT
    private String comment;
    private LocalDateTime assignedAt;
    private LocalDateTime respondedAt;
}
