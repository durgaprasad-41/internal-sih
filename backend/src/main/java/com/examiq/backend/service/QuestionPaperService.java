package com.examiq.backend.service;

import com.examiq.backend.dto.QuestionPaperDto;
import com.examiq.backend.dto.QuestionPaperGenerateRequest;
import com.examiq.backend.dto.QuestionPaperItemDto;
import com.examiq.backend.dto.QuestionPaperTopicRequest;
import com.examiq.backend.dto.QuestionPaperUpdateRequest;
import com.examiq.backend.entity.Question;
import com.examiq.backend.entity.QuestionPaper;
import com.examiq.backend.entity.QuestionPaperItem;
import com.examiq.backend.entity.Subject;
import com.examiq.backend.entity.User;
import com.examiq.backend.repository.QuestionPaperItemRepository;
import com.examiq.backend.repository.QuestionPaperRepository;
import com.examiq.backend.repository.QuestionRepository;
import com.examiq.backend.repository.SubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hybrid question-paper generator: for every question slot the faculty
 * requests (derived from their topic/count breakdown, the requested
 * difficulty distribution, and total marks), first tries to satisfy it from
 * the verified (APPROVED-paper) question bank; any slot the bank can't fill
 * is handed to AiQuestionGenerationService. Nothing about this is specific
 * to any one subject - it works purely off whatever Subject/Question/
 * TopicMapping rows already exist.
 */
@Service
public class QuestionPaperService {

    private static final List<String> DIFFICULTY_ORDER = List.of("EASY", "MEDIUM", "HARD");

    private final QuestionPaperRepository questionPaperRepository;
    private final QuestionPaperItemRepository questionPaperItemRepository;
    private final QuestionRepository questionRepository;
    private final SubjectRepository subjectRepository;
    private final AiQuestionGenerationService aiQuestionGenerationService;

    public QuestionPaperService(QuestionPaperRepository questionPaperRepository,
            QuestionPaperItemRepository questionPaperItemRepository,
            QuestionRepository questionRepository,
            SubjectRepository subjectRepository,
            AiQuestionGenerationService aiQuestionGenerationService) {
        this.questionPaperRepository = questionPaperRepository;
        this.questionPaperItemRepository = questionPaperItemRepository;
        this.questionRepository = questionRepository;
        this.subjectRepository = subjectRepository;
        this.aiQuestionGenerationService = aiQuestionGenerationService;
    }

    @Transactional
    public QuestionPaperDto generate(QuestionPaperGenerateRequest request, User faculty) {
        if (request.getTopics() == null || request.getTopics().isEmpty()) {
            throw new IllegalArgumentException("At least one topic is required");
        }
        int totalQuestions = request.getTopics().stream()
                .mapToInt(t -> t.getNumberOfQuestions() == null ? 0 : t.getNumberOfQuestions())
                .sum();
        if (totalQuestions <= 0) {
            throw new IllegalArgumentException("At least one topic must request at least one question");
        }
        if (request.getTotalMarks() == null || request.getTotalMarks() <= 0) {
            throw new IllegalArgumentException("Total marks must be a positive number");
        }

        Subject subject = resolveSubject(request.getSubject());

        int easyPct = defaultPercent(request.getEasyPercent(), 30);
        int mediumPct = defaultPercent(request.getMediumPercent(), 50);
        int hardPct = defaultPercent(request.getHardPercent(), 20);

        List<String> difficultySequence = buildDifficultySequence(totalQuestions, easyPct, mediumPct, hardPct);
        List<Integer> marksSequence = buildMarksSequence(totalQuestions, request.getTotalMarks());
        List<String> topicSequence = buildTopicSequence(request.getTopics());

        QuestionPaper paper = new QuestionPaper();
        paper.setFaculty(faculty);
        paper.setCollegeName(request.getCollegeName());
        paper.setExamName(request.getExamName());
        paper.setSubject(subject);
        paper.setExamDate(request.getExamDate());
        paper.setTotalMarks(request.getTotalMarks());
        paper.setTotalQuestions(totalQuestions);
        paper.setInstructions(request.getInstructions());
        paper.setEasyPercent(easyPct);
        paper.setMediumPercent(mediumPct);
        paper.setHardPercent(hardPct);
        paper.setStatus("DRAFT");
        QuestionPaper savedPaper = questionPaperRepository.save(paper);

        List<QuestionPaperItem> items = selectItems(subject, topicSequence, difficultySequence, marksSequence,
                request.getInstructions());

        int order = 1;
        for (QuestionPaperItem item : items) {
            item.setQuestionPaper(savedPaper);
            item.setOrderIndex(order++);
            questionPaperItemRepository.save(item);
        }

        return toDto(savedPaper, items);
    }

    /**
     * Fills every (topic, difficulty, marks) slot: bank search first,
     * grouped AI generation for whatever's left. Never selects the same
     * bank question twice within one generated paper.
     */
    private List<QuestionPaperItem> selectItems(Subject subject, List<String> topicSequence,
            List<String> difficultySequence, List<Integer> marksSequence, String instructions) {
        int n = topicSequence.size();
        QuestionPaperItem[] slots = new QuestionPaperItem[n];
        List<Long> usedQuestionIds = new ArrayList<>();
        usedQuestionIds.add(-1L); // JPQL NOT IN requires a non-empty list

        for (int i = 0; i < n; i++) {
            String topic = topicSequence.get(i);
            String difficulty = difficultySequence.get(i);
            List<Question> candidates = questionRepository.findVerifiedCandidates(subject, topic, difficulty,
                    usedQuestionIds);
            if (!candidates.isEmpty()) {
                Question chosen = candidates.get(0);
                usedQuestionIds.add(chosen.getId());
                QuestionPaperItem item = new QuestionPaperItem();
                item.setQuestion(chosen);
                item.setQuestionText(chosen.getQuestionText());
                item.setSource("QUESTION_BANK");
                item.setTopic(topic);
                item.setDifficulty(difficulty);
                item.setMarks(marksSequence.get(i));
                slots[i] = item;
            }
        }

        // Group remaining gaps by (topic, difficulty, marks) so the AI
        // generator is called once per group instead of once per question.
        Map<String, List<Integer>> gapIndexesByGroup = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            if (slots[i] == null) {
                String key = topicSequence.get(i) + " " + difficultySequence.get(i) + " "
                        + marksSequence.get(i);
                gapIndexesByGroup.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
            }
        }

        for (Map.Entry<String, List<Integer>> entry : gapIndexesByGroup.entrySet()) {
            List<Integer> indexes = entry.getValue();
            int firstIndex = indexes.get(0);
            String topic = topicSequence.get(firstIndex);
            String difficulty = difficultySequence.get(firstIndex);
            int marks = marksSequence.get(firstIndex);

            List<AiQuestionGenerationService.GeneratedQuestion> generated = aiQuestionGenerationService.generate(
                    subject.getCanonicalName() != null ? subject.getCanonicalName() : subject.getName(), topic,
                    difficulty, marks, indexes.size(), instructions);

            for (int j = 0; j < indexes.size(); j++) {
                int idx = indexes.get(j);
                AiQuestionGenerationService.GeneratedQuestion gq = j < generated.size() ? generated.get(j) : null;
                QuestionPaperItem item = new QuestionPaperItem();
                item.setQuestion(null);
                item.setQuestionText(gq != null ? gq.text()
                        : "Question pending - AI generation unavailable for this slot.");
                item.setSource("AI_GENERATED");
                item.setTopic(topic);
                item.setDifficulty(difficulty);
                item.setMarks(marks);
                slots[idx] = item;
            }
        }

        List<QuestionPaperItem> ordered = new ArrayList<>(n);
        for (QuestionPaperItem slot : slots) {
            ordered.add(slot);
        }
        return ordered;
    }

    @Transactional(readOnly = true)
    public List<QuestionPaperDto> getForFaculty(User faculty) {
        return questionPaperRepository.findByFacultyOrderByCreatedAtDesc(faculty).stream()
                .map(p -> toDto(p, questionPaperItemRepository.findByQuestionPaperOrderByOrderIndexAsc(p)))
                .toList();
    }

    @Transactional(readOnly = true)
    public QuestionPaperDto getOne(Long id, User faculty) {
        QuestionPaper paper = questionPaperRepository.findByIdAndFaculty(id, faculty)
                .orElseThrow(() -> new IllegalArgumentException("Question paper not found"));
        return toDto(paper, questionPaperItemRepository.findByQuestionPaperOrderByOrderIndexAsc(paper));
    }

    @Transactional
    public QuestionPaperDto update(Long id, QuestionPaperUpdateRequest request, User faculty) {
        QuestionPaper paper = questionPaperRepository.findByIdAndFaculty(id, faculty)
                .orElseThrow(() -> new IllegalArgumentException("Question paper not found"));
        if (!"DRAFT".equals(paper.getStatus())) {
            throw new IllegalArgumentException("Only draft question papers can be edited");
        }

        if (request.getCollegeName() != null && !request.getCollegeName().isBlank()) {
            paper.setCollegeName(request.getCollegeName());
        }
        if (request.getExamName() != null && !request.getExamName().isBlank()) {
            paper.setExamName(request.getExamName());
        }
        if (request.getExamDate() != null) {
            paper.setExamDate(request.getExamDate());
        }
        if (request.getInstructions() != null) {
            paper.setInstructions(request.getInstructions());
        }

        if (request.getItems() != null) {
            List<QuestionPaperItem> existing = questionPaperItemRepository
                    .findByQuestionPaperOrderByOrderIndexAsc(paper);
            Map<Long, QuestionPaperUpdateRequest.ItemEdit> edits = new HashMap<>();
            for (QuestionPaperUpdateRequest.ItemEdit edit : request.getItems()) {
                if (edit.getId() != null) {
                    edits.put(edit.getId(), edit);
                }
            }
            int totalMarks = 0;
            for (QuestionPaperItem item : existing) {
                QuestionPaperUpdateRequest.ItemEdit edit = edits.get(item.getId());
                if (edit == null) {
                    questionPaperItemRepository.delete(item);
                    continue;
                }
                if (edit.getMarks() != null && edit.getMarks() > 0) {
                    item.setMarks(edit.getMarks());
                }
                if (edit.getOrderIndex() != null) {
                    item.setOrderIndex(edit.getOrderIndex());
                }
                questionPaperItemRepository.save(item);
                totalMarks += item.getMarks() != null ? item.getMarks() : 0;
            }
            List<QuestionPaperItem> remaining = questionPaperItemRepository
                    .findByQuestionPaperOrderByOrderIndexAsc(paper);
            paper.setTotalQuestions(remaining.size());
            paper.setTotalMarks(totalMarks);
        }

        QuestionPaper saved = questionPaperRepository.save(paper);
        return toDto(saved, questionPaperItemRepository.findByQuestionPaperOrderByOrderIndexAsc(saved));
    }

    @Transactional
    public QuestionPaperItemDto regenerateItem(Long paperId, Long itemId, User faculty) {
        QuestionPaper paper = questionPaperRepository.findByIdAndFaculty(paperId, faculty)
                .orElseThrow(() -> new IllegalArgumentException("Question paper not found"));
        if (!"DRAFT".equals(paper.getStatus())) {
            throw new IllegalArgumentException("Only draft question papers can be edited");
        }
        QuestionPaperItem item = questionPaperItemRepository.findByQuestionPaperOrderByOrderIndexAsc(paper).stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Question paper item not found"));
        if (!"AI_GENERATED".equals(item.getSource())) {
            throw new IllegalArgumentException("Only AI-generated questions can be regenerated");
        }

        String subjectName = paper.getSubject().getCanonicalName() != null ? paper.getSubject().getCanonicalName()
                : paper.getSubject().getName();
        List<AiQuestionGenerationService.GeneratedQuestion> generated = aiQuestionGenerationService.generate(
                subjectName, item.getTopic(), item.getDifficulty(), item.getMarks(), 1, paper.getInstructions());
        item.setQuestionText(generated.get(0).text());
        QuestionPaperItem saved = questionPaperItemRepository.save(item);
        return toItemDto(saved);
    }

    @Transactional
    public QuestionPaperDto finalizePaper(Long id, User faculty) {
        QuestionPaper paper = questionPaperRepository.findByIdAndFaculty(id, faculty)
                .orElseThrow(() -> new IllegalArgumentException("Question paper not found"));
        if (!"DRAFT".equals(paper.getStatus())) {
            throw new IllegalArgumentException("Question paper is already finalized");
        }
        paper.setStatus("FINALIZED");
        QuestionPaper saved = questionPaperRepository.save(paper);
        return toDto(saved, questionPaperItemRepository.findByQuestionPaperOrderByOrderIndexAsc(saved));
    }

    @Transactional
    public void delete(Long id, User faculty) {
        QuestionPaper paper = questionPaperRepository.findByIdAndFaculty(id, faculty)
                .orElseThrow(() -> new IllegalArgumentException("Question paper not found"));
        if (!"DRAFT".equals(paper.getStatus())) {
            throw new IllegalArgumentException("Only draft question papers can be deleted");
        }
        questionPaperItemRepository.deleteByQuestionPaper(paper);
        questionPaperRepository.delete(paper);
    }

    private Subject resolveSubject(String subjectName) {
        if (subjectName == null || subjectName.isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }
        String trimmed = subjectName.trim();
        return subjectRepository.findByNameIgnoreCase(trimmed)
                .or(() -> subjectRepository.findByCanonicalNameIgnoreCase(trimmed))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown subject '" + trimmed + "'. Choose a subject that already has papers in the system."));
    }

    private int defaultPercent(Integer value, int fallback) {
        return value != null && value >= 0 ? value : fallback;
    }

    /**
     * Builds a length-`total` list of difficulty labels matching the
     * requested percentages as closely as integer rounding allows, via
     * simple round-robin cycling (EASY, MEDIUM, HARD, EASY, ...) so a
     * topic that only gets a few questions still sees a mix rather than a
     * block of one difficulty.
     */
    private List<String> buildDifficultySequence(int total, int easyPct, int mediumPct, int hardPct) {
        int easy = Math.round(total * easyPct / 100f);
        int hard = Math.round(total * hardPct / 100f);
        int medium = total - easy - hard;
        if (medium < 0) {
            medium = 0;
            hard = Math.max(0, total - easy);
        }
        // Correct any rounding drift so the sequence is exactly `total` long.
        int sum = easy + medium + hard;
        medium += (total - sum);
        if (medium < 0) {
            medium = 0;
        }

        List<String> result = new ArrayList<>(total);
        int e = easy, m = medium, h = hard;
        while (result.size() < total && (e > 0 || m > 0 || h > 0)) {
            if (e > 0) {
                result.add("EASY");
                e--;
            }
            if (result.size() < total && m > 0) {
                result.add("MEDIUM");
                m--;
            }
            if (result.size() < total && h > 0) {
                result.add("HARD");
                h--;
            }
        }
        while (result.size() < total) {
            result.add("MEDIUM");
        }
        return result;
    }

    /** Even split of totalMarks across totalQuestions, remainder absorbed by the first few questions. */
    private List<Integer> buildMarksSequence(int totalQuestions, int totalMarks) {
        int base = totalMarks / totalQuestions;
        int remainder = totalMarks % totalQuestions;
        List<Integer> result = new ArrayList<>(totalQuestions);
        for (int i = 0; i < totalQuestions; i++) {
            result.add(base + (i < remainder ? 1 : 0));
        }
        return result;
    }

    private List<String> buildTopicSequence(List<QuestionPaperTopicRequest> topics) {
        List<String> result = new ArrayList<>();
        for (QuestionPaperTopicRequest topic : topics) {
            int count = topic.getNumberOfQuestions() == null ? 0 : topic.getNumberOfQuestions();
            for (int i = 0; i < count; i++) {
                result.add(topic.getTopicName());
            }
        }
        return result;
    }

    private QuestionPaperDto toDto(QuestionPaper paper, List<QuestionPaperItem> items) {
        QuestionPaperDto dto = new QuestionPaperDto();
        dto.setId(paper.getId());
        dto.setCollegeName(paper.getCollegeName());
        dto.setExamName(paper.getExamName());
        dto.setSubjectName(paper.getSubject() != null
                ? (paper.getSubject().getCanonicalName() != null ? paper.getSubject().getCanonicalName()
                        : paper.getSubject().getName())
                : null);
        dto.setExamDate(paper.getExamDate());
        dto.setTotalMarks(paper.getTotalMarks());
        dto.setTotalQuestions(paper.getTotalQuestions());
        dto.setInstructions(paper.getInstructions());
        dto.setEasyPercent(paper.getEasyPercent());
        dto.setMediumPercent(paper.getMediumPercent());
        dto.setHardPercent(paper.getHardPercent());
        dto.setStatus(paper.getStatus());
        dto.setCreatedAt(paper.getCreatedAt());
        dto.setUpdatedAt(paper.getUpdatedAt());
        dto.setItems(items.stream().map(this::toItemDto).toList());
        return dto;
    }

    private QuestionPaperItemDto toItemDto(QuestionPaperItem item) {
        QuestionPaperItemDto dto = new QuestionPaperItemDto();
        dto.setId(item.getId());
        dto.setQuestionId(item.getQuestion() != null ? item.getQuestion().getId() : null);
        dto.setQuestionText(item.getQuestionText());
        dto.setSource(item.getSource());
        dto.setTopic(item.getTopic());
        dto.setDifficulty(item.getDifficulty());
        dto.setMarks(item.getMarks());
        dto.setOrderIndex(item.getOrderIndex());
        return dto;
    }
}
