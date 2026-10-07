package com.smartstock.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
public class KafkaConfiguration {

    @Bean
    NewTopic smartStockBusinessEventsTopic(
            @Value("${smartstock.kafka.business-events-topic}") String topic
    ) {
        return TopicBuilder.name(topic).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic smartStockBusinessEventsDeadLetterTopic(
            @Value("${smartstock.kafka.business-events-topic}") String topic
    ) {
        return TopicBuilder.name(topic + ".DLT").partitions(1).replicas(1).build();
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        @SuppressWarnings({"rawtypes", "unchecked"})
        ConcurrentKafkaListenerContainerFactoryConfigurer rawConfigurer = configurer;
        @SuppressWarnings({"rawtypes", "unchecked"})
        ConsumerFactory rawConsumerFactory = consumerFactory;
        rawConfigurer.configure(factory, rawConsumerFactory);
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition())
        );
        factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 3L)));
        return factory;
    }
}
