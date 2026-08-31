package com.examiq.backend.repository;

import com.examiq.backend.entity.SubjectAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubjectAliasRepository extends JpaRepository<SubjectAlias, Long> {
    Optional<SubjectAlias> findByAlias(String alias);
}
