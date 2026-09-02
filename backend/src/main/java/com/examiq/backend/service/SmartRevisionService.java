package com.examiq.backend.service;

import com.examiq.backend.dto.SmartRevisionQuestionDto;
import com.examiq.backend.dto.SmartRevisionRequest;
import com.examiq.backend.dto.SmartRevisionResponseDto;
import com.examiq.backend.dto.SmartRevisionStudyBlockDto;
import com.examiq.backend.dto.SmartRevisionTopicDto;
import com.examiq.backend.entity.Question;
import com.examiq.backend.entity.Subject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-driven "Smart Revision" recommender for time-constrained students.
 * Deliberately NOT AI-first: every score and every recommendation traces
 * back to real rows in the verified (APPROVED-paper) question bank via
 * QuestionAnalysisService - the same underlying data the Faculty Question
 * Paper Generator draws from. Where there isn't enough real data for a
 * topic, that is reported honestly rather than papered over.
 */
@Service
public class SmartRevisionService {

    // Configurable estimated revision time per question, by difficulty.
    private static final Map<String, Integer> MINUTES_BY_DIFFICULTY = Map.of(
            "EASY", 10,
            "MEDIUM", 15,
            "HARD", 20);
    private static final int DEFAULT_MINUTES = 15;

    private static final double MUST_STUDY_THRESHOLD = 65.0;

    private final QuestionAnalysisService questionAnalysisService;
    private final AiQuestionGenerationService aiQuestionGenerationService;
    private final PaperService paperService;

    public SmartRevisionService(QuestionAnalysisService questionAnalysisService,
            AiQuestionGenerationService aiQuestionGenerationService, PaperService paperService) {
        this.questionAnalysisService = questionAnalysisService;
        this.aiQuestionGenerationService = aiQuestionGenerationService;
        this.paperService = paperService;
    }

    public SmartRevisionResponseDto recommend(SmartRevisionRequest request) {
        Subject subject = resolveSubject(request.getSubject());

        // 1. Gather verified candidates per topic (the "Historical Paper
        // Analysis" + "Question Bank Analysis" step).
        Map<String, List<Question>> candidatesByTopic = new LinkedHashMap<>();
        for (String topic : request.getTopics()) {
            candidatesByTopic.put(topic, questionAnalysisService.findVerifiedQuestions(subject, topic));
        }
        int maxCandidatesAcrossTopics = candidatesByTopic.values().stream().mapToInt(List::size).max().orElse(0);

        // 2. Score every candidate question (the "Priority Scoring" step).
        Map<String, List<ScoredQuestion>> scoredByTopic = new LinkedHashMap<>();
        for (Map.Entry<String, List<Question>> entry : candidatesByTopic.entrySet()) {
            String topic = entry.getKey();
            List<Question> pool = entry.getValue();
            int topicCandidateCount = pool.size();
            List<ScoredQuestion> scored = new ArrayList<>();
            int maxMarksInTopic = pool.stream().mapToInt(q -> q.getMarks() != null ? q.getMarks() : 0).max().orElse(0);
            for (Question q : pool) {
                scored.add(score(q, topic, pool, topicCandidateCount, maxCandidatesAcrossTopics, maxMarksInTopic));
            }
            // Deduplicate near-identical questions within the topic (keep the
            // highest-scored copy of each normalized text).
            Map<String, ScoredQuestion> deduped = new LinkedHashMap<>();
            for (ScoredQuestion sq : scored) {
                String key = normalize(sq.question.getQuestionText());
                ScoredQuestion existing = deduped.get(key);
                if (existing == null || sq.score > existing.score) {
                    deduped.put(key, sq);
                }
            }
            List<ScoredQuestion> deduplicatedList = new ArrayList<>(deduped.values());
            deduplicatedList.sort(Comparator.comparingDouble((ScoredQuestion s) -> s.score).reversed());
            scoredByTopic.put(topic, deduplicatedList);
        }

        // 3. Topic priority, based on real relative candidate volume.
        List<SmartRevisionTopicDto> topicPriorities = new ArrayList<>();
        List<SmartRevisionTopicDto> uncoveredTopics = new ArrayList<>();
        for (String topic : request.getTopics()) {
            int count = candidatesByTopic.get(topic).size();
            SmartRevisionTopicDto dto = new SmartRevisionTopicDto();
            dto.setTopic(topic);
            dto.setCandidateQuestionCount(count);
            if (count == 0) {
                dto.setPriority("NO_DATA");
                dto.setReason("Not enough historical data is available for reliable prioritization of this topic "
                        + "(no verified questions found for '" + topic + "' under " + displayName(subject) + ").");
                dto.setCovered(false);
                uncoveredTopics.add(dto);
            } else {
                double ratio = maxCandidatesAcrossTopics == 0 ? 0 : (double) count / maxCandidatesAcrossTopics;
                if (ratio >= 0.7) {
                    dto.setPriority("HIGH");
                } else if (ratio >= 0.3) {
                    dto.setPriority("MEDIUM");
                } else {
                    dto.setPriority("LOW");
                }
                dto.setReason(count + " verified question(s) found across approved papers for this topic.");
            }
            topicPriorities.add(dto);
        }

        // 4. Time-aware maximum-coverage selection: cover every topic with
        // data first (its single best question), then greedily fill the
        // remaining time budget by score across all topics.
        int remainingMinutes = request.getAvailableMinutes();
        List<SmartRevisionQuestionDto> recommended = new ArrayList<>();
        Map<String, Integer> selectedCountByTopic = new HashMap<>();
        java.util.Set<Long> selectedIds = new java.util.HashSet<>();

        List<String> topicsByPriority = new ArrayList<>(request.getTopics());
        topicsByPriority.sort(Comparator.comparingInt(
                (String t) -> candidatesByTopic.get(t).size()).reversed());

        for (String topic : topicsByPriority) {
            List<ScoredQuestion> pool = scoredByTopic.get(topic);
            if (pool.isEmpty()) {
                continue;
            }
            ScoredQuestion top = pool.get(0);
            int minutes = estimateMinutes(top.question);
            if (minutes <= remainingMinutes) {
                recommended.add(toDto(top, topic, minutes, "Highest-priority representative question for this topic."));
                selectedIds.add(top.question.getId());
                selectedCountByTopic.merge(topic, 1, Integer::sum);
                remainingMinutes -= minutes;
            }
        }

        List<ScoredQuestion> remainingPool = new ArrayList<>();
        for (Map.Entry<String, List<ScoredQuestion>> entry : scoredByTopic.entrySet()) {
            for (ScoredQuestion sq : entry.getValue()) {
                if (!selectedIds.contains(sq.question.getId())) {
                    remainingPool.add(sq);
                }
            }
        }
        remainingPool.sort(Comparator.comparingDouble((ScoredQuestion s) -> s.score).reversed());
        for (ScoredQuestion sq : remainingPool) {
            int minutes = estimateMinutes(sq.question);
            if (minutes > remainingMinutes) {
                continue;
            }
            recommended.add(toDto(sq, sq.topic, minutes, "High value-for-time: strong priority score within your remaining budget."));
            selectedIds.add(sq.question.getId());
            selectedCountByTopic.merge(sq.topic, 1, Integer::sum);
            remainingMinutes -= minutes;
        }

        // 4b. Syllabus-coverage fallback: a topic with zero verified questions
        // stays listed in uncoveredTopics (an honest "no historical data"
        // signal), but - so the student never fully skips a unit just because
        // no past paper happened to cover it - we also generate one clearly
        // AI-labeled practice question per such topic, time budget permitting.
        // This is never presented as a real past-exam question.
        for (SmartRevisionTopicDto uncovered : uncoveredTopics) {
            if (remainingMinutes < DEFAULT_MINUTES) {
                break;
            }
            String topic = uncovered.getTopic();
            List<AiQuestionGenerationService.GeneratedQuestion> generated = aiQuestionGenerationService.generate(
                    displayName(subject), topic, "MEDIUM", 5, 1,
                    "Practice question for syllabus coverage - no verified past-paper question was found for this topic.");
            if (generated.isEmpty()) {
                continue;
            }
            AiQuestionGenerationService.GeneratedQuestion gq = generated.get(0);
            SmartRevisionQuestionDto dto = new SmartRevisionQuestionDto();
            dto.setQuestionId(null);
            dto.setQuestionText(gq.text());
            dto.setTopic(topic);
            dto.setMarks(gq.marks());
            dto.setDifficulty(gq.difficulty());
            dto.setPriorityScore(null);
            dto.setPriorityCategory("SYLLABUS COVERAGE");
            dto.setTier("SYLLABUS_COVERAGE");
            dto.setSource("AI_GENERATED");
            dto.setEstimatedMinutes(DEFAULT_MINUTES);
            dto.setReason("No verified question exists for '" + topic + "' in the approved-paper question bank yet. "
                    + "This is an AI-generated practice question so you still cover this topic, not a record of a "
                    + "real past exam question.");
            dto.setSourcePaperTitle(null);
            recommended.add(dto);
            selectedCountByTopic.merge(topic, 1, Integer::sum);
            remainingMinutes -= DEFAULT_MINUTES;
        }

        for (SmartRevisionTopicDto dto : topicPriorities) {
            int selected = selectedCountByTopic.getOrDefault(dto.getTopic(), 0);
            dto.setSelectedQuestionCount(selected);
            dto.setCovered(selected > 0);
        }

        // 5. Study plan: group the selection into per-topic blocks in
        // priority order, then a final rapid-revision block with leftover time.
        List<SmartRevisionStudyBlockDto> studyPlan = buildStudyPlan(recommended, topicsByPriority, remainingMinutes);

        int totalStudyMinutes = recommended.stream().mapToInt(SmartRevisionQuestionDto::getEstimatedMinutes).sum();
        boolean anyMarksKnown = recommended.stream().anyMatch(r -> r.getMarks() != null);
        Integer marksCoverage = anyMarksKnown
                ? recommended.stream().mapToInt(r -> r.getMarks() != null ? r.getMarks() : 0).sum()
                : null;

        SmartRevisionResponseDto response = new SmartRevisionResponseDto();
        response.setAvailableMinutes(request.getAvailableMinutes());
        response.setTopicsSelected(request.getTopics().size());
        response.setTopicsCovered((int) topicPriorities.stream().filter(SmartRevisionTopicDto::isCovered).count());
        response.setRecommendedQuestionCount(recommended.size());
        response.setEstimatedStudyMinutes(totalStudyMinutes);
        response.setEstimatedMarksCoverage(marksCoverage);
        response.setTopicPriorities(topicPriorities);
        response.setRecommendedQuestions(recommended);
        response.setUncoveredTopics(uncoveredTopics);
        response.setStudyPlan(studyPlan);
        response.setDisclaimer("These are data-driven, high-priority study recommendations based on verified "
                + "questions from approved papers in ExamIQ - not a prediction of the exact questions that will appear.");
        return response;
    }

    private List<SmartRevisionStudyBlockDto> buildStudyPlan(List<SmartRevisionQuestionDto> recommended,
            List<String> topicsInPriorityOrder, int remainingMinutes) {
        List<SmartRevisionStudyBlockDto> blocks = new ArrayList<>();
        Map<String, List<SmartRevisionQuestionDto>> byTopic = new LinkedHashMap<>();
        for (String topic : topicsInPriorityOrder) {
            byTopic.put(topic, new ArrayList<>());
        }
        for (SmartRevisionQuestionDto q : recommended) {
            byTopic.computeIfAbsent(q.getTopic(), t -> new ArrayList<>()).add(q);
        }
        int blockNumber = 1;
        for (Map.Entry<String, List<SmartRevisionQuestionDto>> entry : byTopic.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            int minutes = entry.getValue().stream().mapToInt(SmartRevisionQuestionDto::getEstimatedMinutes).sum();
            SmartRevisionStudyBlockDto block = new SmartRevisionStudyBlockDto();
            block.setLabel("Block " + blockNumber++);
            block.setMinutes(minutes);
            block.setTopic(entry.getKey());
            block.setQuestionIds(entry.getValue().stream().map(SmartRevisionQuestionDto::getQuestionId)
                    .filter(java.util.Objects::nonNull).toList());
            block.setNote(entry.getValue().size() + " question(s) covering " + entry.getKey());
            blocks.add(block);
        }
        if (remainingMinutes > 0 && !recommended.isEmpty()) {
            SmartRevisionStudyBlockDto finalBlock = new SmartRevisionStudyBlockDto();
            finalBlock.setLabel("Final Revision");
            finalBlock.setMinutes(remainingMinutes);
            finalBlock.setTopic(null);
            finalBlock.setQuestionIds(List.of());
            finalBlock.setNote("Rapid review of the highest-priority questions above.");
            blocks.add(finalBlock);
        }
        return blocks;
    }

    private ScoredQuestion score(Question question, String topic, List<Question> topicPool, int topicCandidateCount,
            int maxCandidatesAcrossTopics, int maxMarksInTopic) {
        long repetitions = questionAnalysisService.repetitionCount(question, topicPool);
        double frequencyScore = Math.min(40.0, (repetitions - 1) * 15.0 + (repetitions >= 1 ? 10 : 0));

        int marks = question.getMarks() != null ? question.getMarks() : 0;
        double marksScore = maxMarksInTopic > 0 ? (marks / (double) maxMarksInTopic) * 20.0 : 0;

        double topicImportanceScore = maxCandidatesAcrossTopics > 0
                ? (topicCandidateCount / (double) maxCandidatesAcrossTopics) * 20.0
                : 0;

        double recencyScore = questionAnalysisService.recencyScore(question) * 10.0;

        String difficulty = question.getDifficultyLevel();
        double difficultyScore = "MEDIUM".equalsIgnoreCase(difficulty) ? 5.0
                : (difficulty != null ? 3.0 : 2.0);

        Double sourceRating = questionAnalysisService.sourcePaperRating(question);
        double qualityScore = sourceRating != null ? (sourceRating / 5.0) * 5.0 : 0;

        double total = frequencyScore + marksScore + topicImportanceScore + recencyScore + difficultyScore
                + qualityScore;

        StringBuilder reason = new StringBuilder();
        if (repetitions > 1) {
            reason.append("Appeared ").append(repetitions).append(" times across approved papers. ");
        }
        if (marks > 0) {
            reason.append("Worth ").append(marks).append(" marks. ");
        }
        reason.append("Topic '").append(topic).append("' has ").append(topicCandidateCount)
                .append(" verified question(s) overall.");

        return new ScoredQuestion(question, topic, Math.round(total * 10.0) / 10.0, reason.toString().trim());
    }

    private SmartRevisionQuestionDto toDto(ScoredQuestion sq, String topic, int minutes, String selectionReason) {
        SmartRevisionQuestionDto dto = new SmartRevisionQuestionDto();
        dto.setQuestionId(sq.question.getId());
        dto.setQuestionText(sq.question.getQuestionText());
        dto.setTopic(topic);
        dto.setMarks(sq.question.getMarks());
        dto.setDifficulty(sq.question.getDifficultyLevel());
        dto.setPriorityScore(sq.score);
        dto.setPriorityCategory(sq.score >= 70 ? "HIGH PRIORITY" : sq.score >= 40 ? "MEDIUM PRIORITY" : "LOW PRIORITY");
        dto.setTier(sq.score >= MUST_STUDY_THRESHOLD ? "MUST_STUDY" : "HIGH_PRIORITY");
        dto.setSource("QUESTION_BANK");
        dto.setEstimatedMinutes(minutes);
        dto.setReason(sq.reason + " " + selectionReason);
        dto.setSourcePaperTitle(sq.question.getPaper() != null ? sq.question.getPaper().getTitle() : null);
        return dto;
    }

    private int estimateMinutes(Question question) {
        String difficulty = question.getDifficultyLevel();
        if (difficulty == null) {
            return DEFAULT_MINUTES;
        }
        return MINUTES_BY_DIFFICULTY.getOrDefault(difficulty.toUpperCase(), DEFAULT_MINUTES);
    }

    private Subject resolveSubject(String subjectName) {
        if (subjectName == null || subjectName.isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }
        // Same resolution paper search already uses: exact name/canonical
        // name, a configured alias (e.g. "DBMS"), or an acronym (e.g. "LAFA"
        // for "Linear Algebra And Function Approximation") - so typing a
        // short form here works exactly like it does in Smart Paper Search.
        return paperService.resolveSubjectByNameAliasOrAcronym(subjectName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown subject '" + subjectName.trim()
                                + "'. Choose a subject that already has papers in the system, or use its exact name, "
                                + "a configured alias, or its acronym."));
    }

    private String displayName(Subject subject) {
        return subject.getCanonicalName() != null ? subject.getCanonicalName() : subject.getName();
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

    private record ScoredQuestion(Question question, String topic, double score, String reason) {
    }
}
