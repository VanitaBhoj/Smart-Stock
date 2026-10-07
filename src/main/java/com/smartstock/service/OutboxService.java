package com.smartstock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartstock.entity.OutboxEvent;
import com.smartstock.repository.OutboxEventRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String eventType, String aggregateType, Long aggregateId, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            repository.save(new OutboxEvent(
                    eventType, aggregateType, aggregateId, json, clock.instant()
            ));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize business event " + eventType, ex);
        }
    }
}
