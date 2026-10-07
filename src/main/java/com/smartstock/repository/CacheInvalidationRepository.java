package com.smartstock.repository;

import com.smartstock.entity.CacheInvalidation;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CacheInvalidationRepository extends JpaRepository<CacheInvalidation, Long> {
    List<CacheInvalidation> findAllByOrderByIdAsc(Pageable pageable);
}
