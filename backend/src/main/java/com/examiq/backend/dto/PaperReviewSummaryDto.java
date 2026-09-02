package com.examiq.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class PaperReviewSummaryDto {
    private PaperDto paper;
    private List<PaperReviewAssignmentDto> assignments;
    private int totalAssigned;
    private int responded;
    private int acceptVotes;
    private int rejectVotes;
    /** true once every assigned reviewer has responded. */
    private boolean reviewComplete;
    /** ACCEPT / REJECT / MIXED / PENDING - null while reviewComplete is false. */
    private String recommendation;
}
