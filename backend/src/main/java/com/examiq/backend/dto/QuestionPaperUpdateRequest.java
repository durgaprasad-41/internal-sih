package com.examiq.backend.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class QuestionPaperUpdateRequest {
    private String collegeName;
    private String examName;
    private LocalDate examDate;
    private String instructions;

    // The final desired set of items: existing item ids to keep, with their
    // (possibly edited) marks and order. Any existing item whose id is not
    // present here is removed (this is how "remove a question" and
    // "reorder questions" are both expressed).
    private List<ItemEdit> items;

    @Data
    public static class ItemEdit {
        private Long id;
        private Integer marks;
        private Integer orderIndex;
    }
}
