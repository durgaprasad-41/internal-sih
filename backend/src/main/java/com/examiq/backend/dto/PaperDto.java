package com.examiq.backend.dto;

import lombok.Data;

@Data
public class PaperDto {
    private Long id;
    private String title;
    private String subjectName;
    private String universityName;
    private Integer year;
    private String examType;
    private String author;
    private String status;
    private String displayStatus;
    private String fileUrl;
    private Double averageRating;
    private String uploaderUsername;
    private Double confidenceScore;
    private String reviewReason;

    /**
     * Maps the internal, backward-compatible status literals stored on Paper
     * (PENDING/APPROVED/REJECTED/FLAGGED) to the clearer names shown to users,
     * without renaming the underlying stored values.
     */
    public static String toDisplayStatus(String rawStatus) {
        if (rawStatus == null) {
            return null;
        }
        return switch (rawStatus.toUpperCase()) {
            case "APPROVED" -> "ACCEPTED";
            case "PENDING" -> "PENDING_REVIEW";
            case "FLAGGED", "FLAGSED" -> "PROCESSING";
            case "REJECTED" -> "REJECTED";
            default -> rawStatus;
        };
    }
}
