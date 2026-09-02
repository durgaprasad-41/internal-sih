package com.examiq.backend.controller;

import com.examiq.backend.dto.ApiResponse;
import com.examiq.backend.dto.QuestionPaperDto;
import com.examiq.backend.dto.QuestionPaperGenerateRequest;
import com.examiq.backend.dto.QuestionPaperItemDto;
import com.examiq.backend.dto.QuestionPaperUpdateRequest;
import com.examiq.backend.entity.User;
import com.examiq.backend.security.AuthenticatedUserResolver;
import com.examiq.backend.service.QuestionPaperService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/faculty/question-papers")
public class QuestionPaperController {

    private final QuestionPaperService questionPaperService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public QuestionPaperController(QuestionPaperService questionPaperService,
            AuthenticatedUserResolver authenticatedUserResolver) {
        this.questionPaperService = questionPaperService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @PostMapping("/generate")
    @PreAuthorize("hasRole('FACULTY')")
    public ResponseEntity<ApiResponse<QuestionPaperDto>> generate(
            @Valid @RequestBody QuestionPaperGenerateRequest request) {
        User faculty = authenticatedUserResolver.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("Question paper generated", questionPaperService.generate(request, faculty)));
    }

    @GetMapping
    @PreAuthorize("hasRole('FACULTY')")
    public ResponseEntity<ApiResponse<List<QuestionPaperDto>>> list() {
        User faculty = authenticatedUserResolver.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("Question papers retrieved", questionPaperService.getForFaculty(faculty)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('FACULTY')")
    public ResponseEntity<ApiResponse<QuestionPaperDto>> getOne(@PathVariable Long id) {
        User faculty = authenticatedUserResolver.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("Question paper retrieved", questionPaperService.getOne(id, faculty)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('FACULTY')")
    public ResponseEntity<ApiResponse<QuestionPaperDto>> update(@PathVariable Long id,
            @RequestBody QuestionPaperUpdateRequest request) {
        User faculty = authenticatedUserResolver.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("Question paper updated", questionPaperService.update(id, request, faculty)));
    }

    @PostMapping("/{id}/items/{itemId}/regenerate")
    @PreAuthorize("hasRole('FACULTY')")
    public ResponseEntity<ApiResponse<QuestionPaperItemDto>> regenerateItem(@PathVariable Long id,
            @PathVariable Long itemId) {
        User faculty = authenticatedUserResolver.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("Question regenerated",
                questionPaperService.regenerateItem(id, itemId, faculty)));
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasRole('FACULTY')")
    public ResponseEntity<ApiResponse<QuestionPaperDto>> finalizePaper(@PathVariable Long id) {
        User faculty = authenticatedUserResolver.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("Question paper finalized", questionPaperService.finalizePaper(id, faculty)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('FACULTY')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        User faculty = authenticatedUserResolver.getCurrentUser();
        questionPaperService.delete(id, faculty);
        return ResponseEntity.ok(ApiResponse.success("Question paper deleted", null));
    }
}
