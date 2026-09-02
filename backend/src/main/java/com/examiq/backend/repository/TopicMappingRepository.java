package com.examiq.backend.repository;

import com.examiq.backend.entity.TopicMapping;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicMappingRepository extends JpaRepository<TopicMapping, Long> {
}
