package com.examiq.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates question text for gaps the verified question bank can't fill.
 * Reuses the existing ai-service /ai/generate endpoint (same pattern as
 * PaperService's optional AI calls) as the primary source; since that
 * service is a stub with no real model behind it (confirmed separately -
 * it returns fixed canned text regardless of input) and typically isn't
 * running in this environment, a deterministic local template generator
 * tops up whatever the external call didn't (or couldn't) supply, so the
 * feature always produces a complete, clearly-labeled draft either way.
 */
@Service
public class AiQuestionGenerationService {

    public record GeneratedQuestion(String text, String difficulty, Integer marks) {
    }

    @Value("${app.ai.service-url:http://localhost:8001}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate;

    public AiQuestionGenerationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<GeneratedQuestion> generate(String subjectName, String topic, String difficulty, int marks,
            int count, String instructions) {
        List<GeneratedQuestion> results = new ArrayList<>(tryExternalAi(subjectName, topic, difficulty, marks, count,
                instructions));

        while (results.size() < count) {
            results.add(localPlaceholder(subjectName, topic, difficulty, marks, results.size() + 1));
        }

        return results.size() > count ? results.subList(0, count) : results;
    }

    @SuppressWarnings("unchecked")
    private List<GeneratedQuestion> tryExternalAi(String subjectName, String topic, String difficulty, int marks,
            int count, String instructions) {
        List<GeneratedQuestion> results = new ArrayList<>();
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("text", instructions != null ? instructions : "");
            request.put("query", subjectName + " - " + topic);

            Map<String, Object> response = restTemplate.postForObject(aiServiceUrl + "/ai/generate", request,
                    Map.class);
            if (response == null || !response.containsKey("data")) {
                return results;
            }
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            Object generated = data.get("generated_questions");
            if (!(generated instanceof List<?> list)) {
                return results;
            }
            for (Object item : list) {
                if (results.size() >= count || !(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Object text = map.get("text");
                if (text != null) {
                    results.add(new GeneratedQuestion(text.toString(), difficulty, marks));
                }
            }
        } catch (Exception e) {
            System.out.println("Warning: AI generation service unavailable, using local template generator: "
                    + e.getMessage());
        }
        return results;
    }

    private static final Map<String, List<String>> VERBS_BY_DIFFICULTY = Map.of(
            "EASY", List.of("Define and briefly explain", "State and describe", "List and outline"),
            "HARD", List.of("Critically analyze and evaluate", "Derive and justify", "Compare, contrast and critique"),
            "MEDIUM", List.of("Explain, with suitable examples,", "Illustrate with a worked example",
                    "Discuss the working and significance of"));

    private GeneratedQuestion localPlaceholder(String subjectName, String topic, String difficulty, int marks,
            int sequence) {
        List<String> verbs = VERBS_BY_DIFFICULTY.getOrDefault(
                difficulty == null ? "MEDIUM" : difficulty.toUpperCase(), VERBS_BY_DIFFICULTY.get("MEDIUM"));
        // Genuinely random pick (not time-based) so repeated "regenerate" calls
        // on the same topic/difficulty don't return byte-identical placeholder
        // text and aren't subject to timer-granularity bias.
        String verb = verbs.get(ThreadLocalRandom.current().nextInt(verbs.size()));
        String text = String.format("%s the concept of \"%s\" within %s. (AI-generated draft question #%d - "
                + "please review before finalizing.)", verb, topic, subjectName, sequence);
        return new GeneratedQuestion(text, difficulty, marks);
    }
}
