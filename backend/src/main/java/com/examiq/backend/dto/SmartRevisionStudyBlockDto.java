package com.examiq.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class SmartRevisionStudyBlockDto {
    private String label;
    private int minutes;
    private String topic;
    private List<Long> questionIds;
    private String note;
}
