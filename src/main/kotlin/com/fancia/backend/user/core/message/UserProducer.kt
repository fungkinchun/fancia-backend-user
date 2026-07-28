package com.fancia.backend.user.core.message

import com.fancia.backend.shared.user.core.message.UserDeletedEvent
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.util.*

@Service
class UserProducer(
    private val kafkaTemplate: KafkaTemplate<UUID, Any>
) {
    fun publishUserDeleted(event: UserDeletedEvent) {
        kafkaTemplate.send("users", event.id, event)
            .whenComplete { result, ex -> }
    }
}