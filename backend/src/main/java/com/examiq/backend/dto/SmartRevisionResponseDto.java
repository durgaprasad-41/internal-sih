package com.examiq.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class SmartRevisionResponseDto {
    private int availableMinutes;
    private int topicsSelected;
    private int topicsCovered;
    private int recommendedQuestionCount;
    private int estimatedStudyMinutes;
    private Integer estimatedMarksCoverage; // null if no marks data available at all
    private List<SmartRevisionTopicDto> topicPriorities;
    private List<SmartRevisionQuestionDto> recommendedQuestions;
    private List<SmartRevisionTopicDto> uncoveredTopics;
    private List<SmartRevisionStudyBlockDto> studyPlan;
    private String disclaimer;
}
