package com.examiq.backend.controller;

import com.examiq.backend.dto.ApiResponse;
import com.examiq.backend.entity.Report;
import com.examiq.backend.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/papers/{paperId}/report")
    @PreAuthorize("hasRole('STUDENT') or hasRole('FACULTY')")
    public ResponseEntity<ApiResponse<Report>> reportPaper(
            @PathVariable Long paperId,
            @RequestParam String reportType,
            @RequestParam String description) {
        Report report = reportService.reportPaper(paperId, 1L, reportType, description);
        return ResponseEntity.ok(ApiResponse.success("Paper reported successfully", report));
    }

    @GetMapping("/admin/reports")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Report>>> getOpenReports() {
        List<Report> reports = reportService.getOpenReports();
        return ResponseEntity.ok(ApiResponse.success("Open reports retrieved successfully", reports));
    }

    @GetMapping("/papers/{paperId}/reports")
    public ResponseEntity<ApiResponse<List<Report>>> getPaperReports(@PathVariable Long paperId) {
        List<Report> reports = reportService.getPaperReports(paperId);
        return ResponseEntity.ok(ApiResponse.success("Paper reports retrieved successfully", reports));
    }

    @PutMapping("/admin/reports/{reportId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Report>> updateReportStatus(
            @PathVariable Long reportId,
            @RequestParam String status) {
        Report report = reportService.updateReportStatus(reportId, status);
        return ResponseEntity.ok(ApiResponse.success("Report status updated successfully", report));
    }
}
