package com.fancia.backend.user.core.message

import com.fancia.backend.shared.common.tag.core.message.TagDeletedEvent
import com.fancia.backend.user.core.service.UserService
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class TagConsumer(
    private val userService: UserService
) {
    @KafkaListener(topics = ["tags"], groupId = "deletion")
    fun onTagDeleted(event: TagDeletedEvent) {
        userService.removeTagFromAllUsers(event.id)
    }
}
