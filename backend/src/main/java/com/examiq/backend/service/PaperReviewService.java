package com.examiq.backend.service;

import com.examiq.backend.dto.PaperReviewAssignmentDto;
import com.examiq.backend.dto.PaperReviewSummaryDto;
import com.examiq.backend.entity.AdminAction;
import com.examiq.backend.entity.Paper;
import com.examiq.backend.entity.PaperReviewAssignment;
import com.examiq.backend.entity.User;
import com.examiq.backend.repository.AdminActionRepository;
import com.examiq.backend.repository.PaperRepository;
import com.examiq.backend.repository.PaperReviewAssignmentRepository;
import com.examiq.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Faculty peer-review layer sitting between "paper awaiting review" and the
 * admin's final decision. An admin forwards a PENDING paper to one or more
 * FACULTY reviewers; each reviewer's accept/reject is advisory only - it
 * never changes the paper's status by itself. Once every assigned reviewer
 * has responded, every admin is notified with a recommendation, but the
 * paper stays exactly where it was (UNDER_REVIEW) until an admin calls the
 * existing approve/reject endpoints (AdminService) to make it final. This
 * keeps "admin is the final authority" true by construction: there is no
 * code path anywhere in this service that sets Paper.status to APPROVED.
 */
@Service
public class PaperReviewService {

    private static final Set<String> VALID_DECISIONS = Set.of("ACCEPT", "REJECT");

    private final PaperRepository paperRepository;
    private final UserRepository userRepository;
    private final PaperReviewAssignmentRepository assignmentRepository;
    private final AdminActionRepository adminActionRepository;
    private final NotificationService notificationService;
    private final PaperService paperService;

    public PaperReviewService(PaperRepository paperRepository, UserRepository userRepository,
            PaperReviewAssignmentRepository assignmentRepository, AdminActionRepository adminActionRepository,
            NotificationService notificationService, PaperService paperService) {
        this.paperRepository = paperRepository;
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.adminActionRepository = adminActionRepository;
        this.notificationService = notificationService;
        this.paperService = paperService;
    }

    @Transactional
    public PaperReviewSummaryDto assignReviewers(Long paperId, Long adminId, List<Long> reviewerIds) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new IllegalArgumentException("Paper not found"));
        if (!"PENDING".equalsIgnoreCase(paper.getStatus())) {
            throw new IllegalArgumentException(
                    "Only a paper still awaiting initial review (PENDING) can be forwarded to faculty reviewers. "
                            + "This paper is currently " + paper.getStatus() + ".");
        }
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        List<User> newlyAssigned = new ArrayList<>();
        for (Long reviewerId : reviewerIds) {
            User reviewer = userRepository.findById(reviewerId)
                    .orElseThrow(() -> new IllegalArgumentException("Reviewer with id " + reviewerId + " not found"));
            if (reviewer.getRole() == null || !"FACULTY".equalsIgnoreCase(reviewer.getRole().getName())) {
                throw new IllegalArgumentException(
                        reviewer.getUsername() + " is not a FACULTY account and cannot be assigned as a reviewer.");
            }
            if (assignmentRepository.existsByPaperAndReviewer(paper, reviewer)) {
                continue; // already assigned - idempotent, not an error
            }
            PaperReviewAssignment assignment = new PaperReviewAssignment();
            assignment.setPaper(paper);
            assignment.setReviewer(reviewer);
            assignment.setAssignedBy(admin);
            assignment.setStatus("PENDING");
            assignmentRepository.save(assignment);
            newlyAssigned.add(reviewer);
        }

        if (newlyAssigned.isEmpty() && assignmentRepository.findByPaper(paper).isEmpty()) {
            throw new IllegalArgumentException("No faculty reviewers were assigned.");
        }

        paper.setStatus("UNDER_REVIEW");
        paperRepository.save(paper);

        AdminAction action = new AdminAction();
        action.setAdmin(admin);
        action.setPaper(paper);
        action.setActionType("FORWARD_FOR_REVIEW");
        action.setReason("Forwarded to " + newlyAssigned.size() + " faculty reviewer(s) for expert review.");
        adminActionRepository.save(action);

        for (User reviewer : newlyAssigned) {
            notificationService.createNotification(reviewer.getId(), "Paper Review Requested",
                    "An administrator has asked you to review '" + paper.getTitle() + "' ("
                            + (paper.getSubject() != null ? paper.getSubject().getCanonicalName() : "Unknown subject")
                            + "). Please accept or reject with your recommendation.",
                    "REVIEW_REQUESTED", paper.getId());
        }

        return buildSummary(paper);
    }

    @Transactional
    public PaperReviewAssignmentDto submitReviewByAssignment(Long assignmentId, Long facultyUserId, String decision,
            String comment) {
        String normalizedDecision = decision == null ? "" : decision.trim().toUpperCase();
        if (!VALID_DECISIONS.contains(normalizedDecision)) {
            throw new IllegalArgumentException("Decision must be ACCEPT or REJECT");
        }
        PaperReviewAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Review assignment not found"));
        if (!assignment.getReviewer().getId().equals(facultyUserId)) {
            throw new IllegalArgumentException("This review assignment does not belong to you.");
        }

        assignment.setStatus(normalizedDecision);
        assignment.setComment(comment);
        assignment.setRespondedAt(LocalDateTime.now());
        assignmentRepository.save(assignment);

        notifyAdminsIfReviewComplete(assignment.getPaper());

        return toAssignmentDto(assignment);
    }

    private void notifyAdminsIfReviewComplete(Paper paper) {
        List<PaperReviewAssignment> assignments = assignmentRepository.findByPaper(paper);
        boolean allResponded = assignments.stream().noneMatch(a -> "PENDING".equalsIgnoreCase(a.getStatus()));
        if (!allResponded || assignments.isEmpty()) {
            return;
        }
        long acceptVotes = assignments.stream().filter(a -> "ACCEPT".equalsIgnoreCase(a.getStatus())).count();
        long rejectVotes = assignments.stream().filter(a -> "REJECT".equalsIgnoreCase(a.getStatus())).count();
        String recommendation = acceptVotes > rejectVotes ? "acceptance" : rejectVotes > acceptVotes ? "rejection" : "mixed feedback (no clear majority)";

        String message = "Faculty review complete for '" + paper.getTitle() + "': " + acceptVotes + " accept, "
                + rejectVotes + " reject out of " + assignments.size() + " reviewer(s). Recommended for "
                + recommendation + ". Your final decision is still required.";

        List<User> admins = userRepository.findByRole_NameIgnoreCase("ADMIN");
        for (User admin : admins) {
            try {
                notificationService.createNotification(admin.getId(), "Paper Review Recommendation Ready", message,
                        "PAPER_REVIEW_COMPLETE", paper.getId());
            } catch (Exception e) {
                System.err.println("Failed to notify admin " + admin.getUsername() + ": " + e.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<PaperReviewAssignmentDto> getAssignmentsForFaculty(Long facultyUserId) {
        User reviewer = userRepository.findById(facultyUserId)
                .orElseThrow(() -> new IllegalArgumentException("Reviewer not found"));
        return assignmentRepository.findByReviewerOrderByAssignedAtDesc(reviewer).stream()
                .map(this::toAssignmentDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaperReviewSummaryDto getReviewSummary(Long paperId) {
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new IllegalArgumentException("Paper not found"));
        return buildSummary(paper);
    }

    @Transactional(readOnly = true)
    public List<PaperReviewSummaryDto> getUnderReviewPapers() {
        return paperRepository
                .findByStatusOrderByCreatedAtDesc("UNDER_REVIEW",
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::buildSummary)
                .toList();
    }

    private PaperReviewSummaryDto buildSummary(Paper paper) {
        List<PaperReviewAssignment> assignments = assignmentRepository.findByPaper(paper);
        long accept = assignments.stream().filter(a -> "ACCEPT".equalsIgnoreCase(a.getStatus())).count();
        long reject = assignments.stream().filter(a -> "REJECT".equalsIgnoreCase(a.getStatus())).count();
        long responded = accept + reject;
        boolean complete = !assignments.isEmpty() && responded == assignments.size();

        PaperReviewSummaryDto dto = new PaperReviewSummaryDto();
        dto.setPaper(paperService.toPaperDto(paper));
        dto.setAssignments(assignments.stream().map(this::toAssignmentDto).toList());
        dto.setTotalAssigned(assignments.size());
        dto.setResponded((int) responded);
        dto.setAcceptVotes((int) accept);
        dto.setRejectVotes((int) reject);
        dto.setReviewComplete(complete);
        dto.setRecommendation(!complete ? null : accept > reject ? "ACCEPT" : reject > accept ? "REJECT" : "MIXED");
        return dto;
    }

    private PaperReviewAssignmentDto toAssignmentDto(PaperReviewAssignment a) {
        PaperReviewAssignmentDto dto = new PaperReviewAssignmentDto();
        dto.setId(a.getId());
        dto.setPaperId(a.getPaper().getId());
        dto.setPaperTitle(a.getPaper().getTitle());
        dto.setSubjectName(a.getPaper().getSubject() != null ? a.getPaper().getSubject().getCanonicalName() : null);
        dto.setReviewerId(a.getReviewer().getId());
        dto.setReviewerUsername(a.getReviewer().getUsername());
        dto.setReviewerFullName(a.getReviewer().getFullName());
        dto.setStatus(a.getStatus());
        dto.setComment(a.getComment());
        dto.setAssignedAt(a.getAssignedAt());
        dto.setRespondedAt(a.getRespondedAt());
        return dto;
    }
}
