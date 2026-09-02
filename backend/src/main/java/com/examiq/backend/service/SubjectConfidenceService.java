package com.examiq.backend.service;

import com.examiq.backend.entity.Subject;
import com.examiq.backend.entity.SubjectAlias;
import com.examiq.backend.repository.SubjectAliasRepository;
import com.examiq.backend.repository.SubjectRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Subject-agnostic, database-driven confidence engine. Decides whether an
 * uploaded paper's title + extracted content matches the subject the
 * uploader selected, by comparing against every Subject/SubjectAlias already
 * known to the system - no subject is special-cased in code. New subjects
 * automatically participate as soon as they exist as rows, exactly like the
 * rest of the subject-resolution logic in PaperService already works.
 */
@Service
public class SubjectConfidenceService {

    public enum Decision {
        HIGH_MATCH,
        UNCERTAIN,
        HIGH_MISMATCH
    }

    public record ConfidenceResult(Decision decision, double score, String reason) {
        public boolean isPassed() {
            return decision == Decision.HIGH_MATCH;
        }
    }

    // Generic exam/paper vocabulary that would appear regardless of subject -
    // excluded so it never counts as evidence of any particular subject.
    private static final Set<String> GENERIC_STOPWORDS = new HashSet<>(Arrays.asList(
            "exam", "exams", "examination", "paper", "papers", "question", "questions",
            "test", "tests", "unit", "units", "time", "hours", "university", "college",
            "semester", "final", "marks", "section", "sections", "answer", "answers",
            "attempt", "department", "mid", "end", "year", "date", "roll", "name"));

    private final SubjectRepository subjectRepository;
    private final SubjectAliasRepository subjectAliasRepository;

    public SubjectConfidenceService(SubjectRepository subjectRepository,
            SubjectAliasRepository subjectAliasRepository) {
        this.subjectRepository = subjectRepository;
        this.subjectAliasRepository = subjectAliasRepository;
    }

    public ConfidenceResult evaluate(Subject selectedSubject, String title, String extractedText) {
        String haystack = normalize(orEmpty(title) + " " + orEmpty(extractedText));
        Set<String> haystackWords = wordsOf(haystack);

        String selectedPhrase = normalize(displayName(selectedSubject));
        Set<String> selectedTerms = significantTerms(selectedSubject);
        boolean selectedPhraseHit = !selectedPhrase.isBlank() && haystack.contains(selectedPhrase);
        long selectedWordHits = selectedTerms.stream().filter(haystackWords::contains).count();
        boolean selectedStrong = selectedPhraseHit || selectedWordHits >= 2;

        Subject bestCompetitor = null;
        long bestCompetitorHits = 0;
        boolean bestCompetitorPhraseHit = false;

        for (Subject other : subjectRepository.findAll()) {
            if (other.getId().equals(selectedSubject.getId())) {
                continue;
            }
            String otherPhrase = normalize(displayName(other));
            Set<String> otherTerms = significantTerms(other);
            boolean phraseHit = !otherPhrase.isBlank() && haystack.contains(otherPhrase);
            long hits = otherTerms.stream().filter(haystackWords::contains).count();

            long candidateStrength = hits + (phraseHit ? 3 : 0);
            long bestStrength = bestCompetitorHits + (bestCompetitorPhraseHit ? 3 : 0);
            if (candidateStrength > bestStrength) {
                bestCompetitor = other;
                bestCompetitorHits = hits;
                bestCompetitorPhraseHit = phraseHit;
            }
        }

        boolean competitorStrong = bestCompetitor != null && (bestCompetitorPhraseHit || bestCompetitorHits >= 2);

        if (selectedStrong && !competitorStrong) {
            double score = Math.min(0.98, 0.75 + (selectedPhraseHit ? 0.15 : 0) + Math.min(0.08, selectedWordHits * 0.02));
            return new ConfidenceResult(Decision.HIGH_MATCH, score,
                    "Paper content matches the selected subject '" + displayName(selectedSubject) + "'.");
        }

        if (competitorStrong && !selectedStrong) {
            double score = Math.min(0.95, 0.7 + (bestCompetitorPhraseHit ? 0.15 : 0) + Math.min(0.08, bestCompetitorHits * 0.02));
            return new ConfidenceResult(Decision.HIGH_MISMATCH, score,
                    "Your paper is not relevant to the selected subject '" + displayName(selectedSubject)
                            + "'. Its content appears to be related to " + displayName(bestCompetitor) + " instead.");
        }

        double score = (selectedWordHits > 0 || selectedPhraseHit) ? 0.55 : 0.4;
        return new ConfidenceResult(Decision.UNCERTAIN, score,
                "The system could not confidently validate whether this paper matches '"
                        + displayName(selectedSubject) + "'. It has been sent to an administrator for review.");
    }

    private Set<String> significantTerms(Subject subject) {
        Set<String> terms = new HashSet<>();
        addWords(terms, subject.getName());
        addWords(terms, subject.getCanonicalName());
        List<SubjectAlias> aliases = subjectAliasRepository.findBySubject(subject);
        if (aliases != null) {
            aliases.forEach(alias -> addWords(terms, alias.getAlias()));
        }
        return terms;
    }

    private void addWords(Set<String> target, String text) {
        if (text == null) {
            return;
        }
        for (String word : wordsOf(normalize(text))) {
            if (word.length() >= 4 && !GENERIC_STOPWORDS.contains(word)) {
                target.add(word);
            }
        }
    }

    private Set<String> wordsOf(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return new HashSet<>(Arrays.asList(text.split("[^a-z0-9]+")));
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String orEmpty(String text) {
        return text == null ? "" : text;
    }

    private String displayName(Subject subject) {
        return subject.getCanonicalName() != null ? subject.getCanonicalName() : subject.getName();
    }
}
