package com.examiq.backend.controller;

import com.examiq.backend.dto.ApiResponse;
import com.examiq.backend.dto.PaperDto;
import com.examiq.backend.dto.PaperReviewAssignmentDto;
import com.examiq.backend.dto.SubmitReviewRequest;
import com.examiq.backend.entity.FacultyVerification;
import com.examiq.backend.security.AuthenticatedUserResolver;
import com.examiq.backend.service.FacultyService;
import com.examiq.backend.service.PaperReviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FacultyController {

    private final FacultyService facultyService;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final PaperReviewService paperReviewService;

    public FacultyController(FacultyService facultyService, AuthenticatedUserResolver authenticatedUserResolver,
            PaperReviewService paperReviewService) {
        this.facultyService = facultyService;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.paperReviewService = paperReviewService;
    }

    @GetMapping("/faculty/dashboard")
    @PreAuthorize("hasRole('FACULTY')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboard() {
        Long facultyId = authenticatedUserResolver.getCurrentUser().getId();
        return ResponseEntity.ok(ApiResponse.success("Faculty dashboard loaded", facultyService.getDashboardSummary(facultyId)));
    }

    @PostMapping("/faculty/verification")
    @PreAuthorize("hasRole('FACULTY')")
    public ResponseEntity<ApiResponse<FacultyVerification>> submitVerification(
            @RequestParam(required = false) Long universityId,
            @RequestParam String documentsUrl) {
        Long facultyId = authenticatedUserResolver.getCurrentUser().getId();
        FacultyVerification verification = facultyService.submitVerificationRequest(facultyId, universityId,
                documentsUrl);
        return ResponseEntity.ok(ApiResponse.success("Verification request submitted successfully", verification));
    }

    @GetMapping("/faculty/analytics")
    @PreAuthorize("hasRole('FACULTY')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAnalytics() {
        Long facultyId = authenticatedUserResolver.getCurrentUser().getId();
        Map<String, Object> analytics = facultyService.getFacultyAnalytics(facultyId);
        return ResponseEntity.ok(ApiResponse.success("Faculty analytics retrieved successfully", analytics));
    }

    @GetMapping("/faculty/uploads")
    @PreAuthorize("hasRole('FACULTY')")
    public ResponseEntity<ApiResponse<List<PaperDto>>> getFacultyUploads() {
        Long facultyId = authenticatedUserResolver.getCurrentUser().getId();
        List<PaperDto> papers = facultyService.getFacultyUploads(facultyId);
        return ResponseEntity.ok(ApiResponse.success("Faculty uploads retrieved successfully", papers));
    }

    @GetMapping("/faculty/reviews")
    @PreAuthorize("hasRole('FACULTY')")
    public ResponseEntity<ApiResponse<List<PaperReviewAssignmentDto>>> getMyReviewAssignments() {
        Long facultyId = authenticatedUserResolver.getCurrentUser().getId();
        return ResponseEntity.ok(ApiResponse.success("Review assignments retrieved successfully",
                paperReviewService.getAssignmentsForFaculty(facultyId)));
    }

    @PutMapping("/faculty/reviews/{assignmentId}")
    @PreAuthorize("hasRole('FACULTY')")
    public ResponseEntity<ApiResponse<PaperReviewAssignmentDto>> submitReview(
            @PathVariable Long assignmentId,
            @Valid @RequestBody SubmitReviewRequest request) {
        Long facultyId = authenticatedUserResolver.getCurrentUser().getId();
        return ResponseEntity.ok(ApiResponse.success("Review submitted successfully",
                paperReviewService.submitReviewByAssignment(assignmentId, facultyId, request.getDecision(),
                        request.getComment())));
    }

    @GetMapping("/admin/faculty-verification/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<FacultyVerification>>> getPendingVerifications() {
        List<FacultyVerification> verifications = facultyService.getPendingVerifications();
        return ResponseEntity.ok(ApiResponse.success("Pending verifications retrieved successfully", verifications));
    }

    @PutMapping("/admin/faculty-verification/{verificationId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FacultyVerification>> approveVerification(@PathVariable Long verificationId) {
        Long adminId = authenticatedUserResolver.getCurrentUser().getId();
        FacultyVerification verification = facultyService.approveFacultyVerification(verificationId, adminId);
        return ResponseEntity.ok(ApiResponse.success("Faculty verification approved successfully", verification));
    }
}
