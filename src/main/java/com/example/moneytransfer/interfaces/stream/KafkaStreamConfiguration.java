package com.example.moneytransfer.interfaces.stream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration
@EnableKafka
@ConditionalOnProperty(prefix = "money-transfer.stream.kafka", name = "enabled", havingValue = "true")
class KafkaStreamConfiguration {
//    Only activate if money-transfer.stream.kafka.enabled=true i.e.
//    TransferStreamConsumer is NOT created then Kafka listener does NOT start
}
