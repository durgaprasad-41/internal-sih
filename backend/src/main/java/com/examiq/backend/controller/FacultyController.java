package com.examiq.backend.controller;

import com.examiq.backend.dto.ApiResponse;
import com.examiq.backend.entity.FacultyVerification;
import com.examiq.backend.entity.Paper;
import com.examiq.backend.service.FacultyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FacultyController {

    private final FacultyService facultyService;

    public FacultyController(FacultyService facultyService) {
        this.facultyService = facultyService;
    }

    @GetMapping("/faculty/dashboard")
    @PreAuthorize("hasRole('FACULTY')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboard() {
        return ResponseEntity.ok(ApiResponse.success("Faculty dashboard loaded", Map.of(
                "papersUploaded", 18,
                "downloads", 2800,
                "verification", "APPROVED")));
    }

    @PostMapping("/faculty/verification")
    @PreAuthorize("hasRole('FACULTY')")
    public ResponseEntity<ApiResponse<FacultyVerification>> submitVerification(
            @RequestParam(required = false) Long universityId,
            @RequestParam String documentsUrl) {
        FacultyVerification verification = facultyService.submitVerificationRequest(1L, universityId, documentsUrl);
        return ResponseEntity.ok(ApiResponse.success("Verification request submitted successfully", verification));
    }

    @GetMapping("/faculty/analytics")
    @PreAuthorize("hasRole('FACULTY')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAnalytics() {
        Map<String, Object> analytics = facultyService.getFacultyAnalytics(1L);
        return ResponseEntity.ok(ApiResponse.success("Faculty analytics retrieved successfully", analytics));
    }

    @GetMapping("/faculty/uploads")
    @PreAuthorize("hasRole('FACULTY')")
    public ResponseEntity<ApiResponse<List<Paper>>> getFacultyUploads() {
        List<Paper> papers = facultyService.getFacultyUploads(1L);
        return ResponseEntity.ok(ApiResponse.success("Faculty uploads retrieved successfully", papers));
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
        FacultyVerification verification = facultyService.approveFacultyVerification(verificationId, 1L);
        return ResponseEntity.ok(ApiResponse.success("Faculty verification approved successfully", verification));
    }
}
