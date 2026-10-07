package com.smartstock.repository;

import com.smartstock.entity.ProcessedKafkaEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedKafkaEventRepository extends JpaRepository<ProcessedKafkaEvent, Long> {
}
