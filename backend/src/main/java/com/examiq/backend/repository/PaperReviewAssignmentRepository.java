package com.examiq.backend.repository;

import com.examiq.backend.entity.Paper;
import com.examiq.backend.entity.PaperReviewAssignment;
import com.examiq.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaperReviewAssignmentRepository extends JpaRepository<PaperReviewAssignment, Long> {

    List<PaperReviewAssignment> findByPaper(Paper paper);

    List<PaperReviewAssignment> findByReviewerOrderByAssignedAtDesc(User reviewer);

    Optional<PaperReviewAssignment> findByPaperAndReviewer(Paper paper, User reviewer);

    boolean existsByPaperAndReviewer(Paper paper, User reviewer);

    long countByPaperAndStatus(Paper paper, String status);
}
