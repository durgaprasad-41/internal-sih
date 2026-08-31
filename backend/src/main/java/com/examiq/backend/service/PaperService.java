package com.examiq.backend.service;

import com.examiq.backend.dto.PaperDto;
import com.examiq.backend.entity.Paper;
import com.examiq.backend.entity.Subject;
import com.examiq.backend.entity.University;
import com.examiq.backend.entity.Upload;
import com.examiq.backend.entity.User;
import com.examiq.backend.repository.PaperRepository;
import com.examiq.backend.repository.RatingRepository;
import com.examiq.backend.repository.SubjectRepository;
import com.examiq.backend.repository.UniversityRepository;
import com.examiq.backend.repository.UploadRepository;
import com.examiq.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class PaperService {

    private final PaperRepository paperRepository;
    private final SubjectRepository subjectRepository;
    private final UniversityRepository universityRepository;
    private final UserRepository userRepository;
    private final UploadRepository uploadRepository;
    private final RatingRepository ratingRepository;

    @Value("${app.storage.path:./storage}")
    private String storagePath;

    @Value("${app.ai.service-url:http://localhost:8001}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate;

    public PaperService(PaperRepository paperRepository,
            SubjectRepository subjectRepository,
            UniversityRepository universityRepository,
            UserRepository userRepository,
            UploadRepository uploadRepository,
            RatingRepository ratingRepository,
            RestTemplate restTemplate) {
        this.paperRepository = paperRepository;
        this.subjectRepository = subjectRepository;
        this.universityRepository = universityRepository;
        this.userRepository = userRepository;
        this.uploadRepository = uploadRepository;
        this.ratingRepository = ratingRepository;
        this.restTemplate = restTemplate;
    }

    @Transactional
    public PaperDto uploadPaper(MultipartFile file,
            String title,
            String subjectName,
            String universityName,
            Integer year,
            String examType,
            String author,
            String username) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A paper file is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Paper title is required");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("User not authenticated");
        }

        String normalizedSubject = normalizeOptionalText(subjectName, "General");
        String normalizedUniversity = normalizeOptionalText(universityName, "Unknown University");

        User uploader = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Uploader not found"));

        Subject subject = subjectRepository.findByNameIgnoreCase(normalizedSubject)
                .or(() -> subjectRepository.findByCanonicalNameIgnoreCase(normalizedSubject))
                .or(() -> subjectRepository.findByName(normalizedSubject))
                .or(() -> subjectRepository.findByCanonicalName(normalizedSubject))
                .orElseGet(() -> {
                    Subject newSubject = new Subject();
                    newSubject.setName(normalizedSubject);
                    newSubject.setCanonicalName(normalizedSubject);
                    return subjectRepository.save(newSubject);
                });

        University university = universityRepository.findByNameIgnoreCase(normalizedUniversity)
                .orElseGet(() -> {
                    University newUniversity = new University();
                    newUniversity.setName(normalizedUniversity);
                    return universityRepository.save(newUniversity);
                });

        // Check for duplicate papers
        if (paperRepository.existsByTitleAndSubjectAndUniversityAndYearAndExamType(
                title, subject, university, year, examType)) {
            throw new IllegalArgumentException(
                    "A paper with the same title, subject, university, year, and exam type already exists");
        }

        // Check subject relevance using AI service and auto-approve if relevant
        boolean aiApproved = false;
        try {
            String subjectCheckUrl = aiServiceUrl + "/ai/subject-check";
            java.util.Map<String, Object> request = new java.util.HashMap<>();
            request.put("text", title);
            request.put("query", subject.getCanonicalName());

            java.util.Map<String, Object> response = restTemplate.postForObject(subjectCheckUrl, request,
                    java.util.Map.class);
            if (response != null && response.containsKey("data")) {
                java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.get("data");
                Double matchScore = (Double) data.get("match_score");
                if (matchScore != null && matchScore >= 0.7) {
                    // High confidence - auto-approve
                    aiApproved = true;
                } else if (matchScore != null && matchScore < 0.5) {
                    // Low confidence - reject
                    throw new IllegalArgumentException("Paper does not appear to be relevant to the subject "
                            + subject.getCanonicalName() + ". Match score: " + matchScore);
                }
                // Medium confidence (0.5-0.7) - keep as PENDING for admin review
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            // Log warning but don't fail upload if AI service is unavailable
            System.out.println("Warning: AI subject check failed: " + e.getMessage());
        }

        String fileName = System.currentTimeMillis() + "_"
                + Objects.requireNonNull(file.getOriginalFilename()).replaceAll("\\s+", "_");
        Path path = Paths.get(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(path);
            Path target = path.resolve(fileName);
            Files.copy(file.getInputStream(), target);

            // Calculate SHA-256 hash of the file
            String fileHash = calculateFileHash(target);

            // Check for exact duplicate by file hash
            if (paperRepository.existsByFileHash(fileHash)) {
                Files.deleteIfExists(target);
                throw new IllegalArgumentException(
                        "This file has already been uploaded (duplicate detected by file hash)");
            }

            Paper paper = new Paper();
            paper.setTitle(title);
            paper.setSubject(subject);
            paper.setUniversity(university);
            paper.setUploader(uploader);
            paper.setYear(year);
            paper.setExamType(examType);
            paper.setAuthor(author != null ? author : uploader.getFullName());
            paper.setStatus(aiApproved ? "APPROVED" : "PENDING");
            paper.setFileUrl("/files/" + fileName);
            paper.setFileHash(fileHash);
            Paper savedPaper = paperRepository.save(paper);

            Upload upload = new Upload();
            upload.setPaper(savedPaper);
            upload.setUploadedBy(uploader);
            upload.setOriginalFileName(file.getOriginalFilename());
            upload.setStoredPath(target.toString());
            upload.setFileHash(fileHash);
            upload.setMimeType(file.getContentType());
            upload.setFileSize(file.getSize());
            upload.setUploadStatus("COMPLETED");
            uploadRepository.save(upload);

            return toDto(savedPaper);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to store paper file", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to calculate file hash", e);
        }
    }

    public List<PaperDto> searchPapers(String query) {
        String q = query == null ? "" : query.trim();
        List<Paper> papers = paperRepository.findAll();

        if (q.isEmpty()) {
            return papers.stream()
                    .filter(paper -> "APPROVED".equalsIgnoreCase(paper.getStatus()))
                    .filter(paper -> paper.getFileUrl() != null && !paper.getFileUrl().isBlank())
                    .filter(paper -> uploadRepository.existsByPaper(paper))
                    .map(this::toDto)
                    .collect(Collectors.toList());
        }

        String needle = q.toLowerCase();
        return papers.stream()
                .filter(paper -> "APPROVED".equalsIgnoreCase(paper.getStatus()))
                .filter(paper -> paper.getFileUrl() != null && !paper.getFileUrl().isBlank())
                .filter(paper -> uploadRepository.existsByPaper(paper))
                .filter(paper -> matchesSearch(paper, needle))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<PaperDto> getAllApprovedPapers() {
        return searchPapers("");
    }

    public List<PaperDto> getRecentPapers(int limit) {
        return paperRepository.findByStatusOrderByCreatedAtDesc("APPROVED", Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .filter(paper -> paper.getFileUrl() != null && !paper.getFileUrl().isBlank())
                .filter(paper -> uploadRepository.existsByPaper(paper))
                .limit(limit)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public PaperDto getPaperById(Long id) {
        Paper paper = paperRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Paper not found"));
        if (paper.getFileUrl() == null || paper.getFileUrl().isBlank() || !uploadRepository.existsByPaper(paper)) {
            throw new IllegalArgumentException("Paper file is not available");
        }
        return toDto(paper);
    }

    public Double getAverageRating(Paper paper) {
        double avg = ratingRepository.findByPaper(paper).stream()
                .mapToDouble(r -> r.getScore())
                .average()
                .orElse(0.0);
        return avg == 0.0 ? 0.0 : Math.round(avg * 10.0) / 10.0;
    }

    private String normalizeOptionalText(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    private boolean matchesSearch(Paper paper, String query) {
        String title = paper.getTitle() == null ? "" : paper.getTitle().toLowerCase();
        String subject = paper.getSubject() != null && paper.getSubject().getCanonicalName() != null
                ? paper.getSubject().getCanonicalName().toLowerCase()
                : "";
        String university = paper.getUniversity() != null && paper.getUniversity().getName() != null
                ? paper.getUniversity().getName().toLowerCase()
                : "";
        String examType = paper.getExamType() == null ? "" : paper.getExamType().toLowerCase();
        String author = paper.getAuthor() == null ? "" : paper.getAuthor().toLowerCase();
        return title.contains(query)
                || subject.contains(query)
                || university.contains(query)
                || examType.contains(query)
                || author.contains(query);
    }

    private PaperDto toDto(Paper paper) {
        PaperDto dto = new PaperDto();
        dto.setId(paper.getId());
        dto.setTitle(paper.getTitle());
        dto.setSubjectName(paper.getSubject() != null ? paper.getSubject().getCanonicalName() : "Unknown");
        dto.setUniversityName(paper.getUniversity() != null ? paper.getUniversity().getName() : "Unknown");
        dto.setYear(paper.getYear());
        dto.setExamType(paper.getExamType());
        dto.setAuthor(paper.getAuthor());
        dto.setStatus(paper.getStatus());
        dto.setFileUrl(paper.getFileUrl());
        dto.setAverageRating(getAverageRating(paper));
        return dto;
    }

    private String calculateFileHash(Path filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] fileBytes = Files.readAllBytes(filePath);
        byte[] hashBytes = digest.digest(fileBytes);
        return HexFormat.of().formatHex(hashBytes);
    }
}
