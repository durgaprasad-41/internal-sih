package com.examiq.backend.repository;

import com.examiq.backend.entity.Paper;
import com.examiq.backend.entity.TopicMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TopicMappingRepository extends JpaRepository<TopicMapping, Long> {

    List<TopicMapping> findByQuestion_Paper(Paper paper);
}
