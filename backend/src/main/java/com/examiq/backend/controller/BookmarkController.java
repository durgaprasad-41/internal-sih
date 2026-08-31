package com.examiq.backend.controller;

import com.examiq.backend.dto.ApiResponse;
import com.examiq.backend.entity.Bookmark;
import com.examiq.backend.service.BookmarkService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    public BookmarkController(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    @PostMapping("/papers/{paperId}/bookmark")
    @PreAuthorize("hasRole('STUDENT') or hasRole('FACULTY')")
    public ResponseEntity<ApiResponse<Bookmark>> addBookmark(@PathVariable Long paperId) {
        Bookmark bookmark = bookmarkService.addBookmark(paperId, 1L);
        return ResponseEntity.ok(ApiResponse.success("Paper bookmarked successfully", bookmark));
    }

    @DeleteMapping("/papers/{paperId}/bookmark")
    @PreAuthorize("hasRole('STUDENT') or hasRole('FACULTY')")
    public ResponseEntity<ApiResponse<Void>> removeBookmark(@PathVariable Long paperId) {
        bookmarkService.removeBookmark(paperId, 1L);
        return ResponseEntity.ok(ApiResponse.success("Bookmark removed successfully", null));
    }

    @GetMapping("/papers/{paperId}/is-bookmarked")
    @PreAuthorize("hasRole('STUDENT') or hasRole('FACULTY')")
    public ResponseEntity<ApiResponse<Boolean>> isBookmarked(@PathVariable Long paperId) {
        boolean bookmarked = bookmarkService.isBookmarked(paperId, 1L);
        return ResponseEntity.ok(ApiResponse.success("Bookmark status retrieved successfully", bookmarked));
    }
}
