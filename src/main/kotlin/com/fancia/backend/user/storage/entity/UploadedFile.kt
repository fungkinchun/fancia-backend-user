package com.fancia.backend.user.storage.entity

import com.fancia.backend.shared.common.core.entity.AbstractEntity
import com.fancia.backend.shared.user.core.entity.User
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Entity
import jakarta.persistence.ManyToOne
import org.apache.commons.io.FilenameUtils
import java.time.LocalDateTime
import java.util.*

@Entity
class UploadedFile(
    private val originalFileName: String?,
    private val size: Long?,
    @ManyToOne
    @JsonIgnore
    private val user: User
) : AbstractEntity() {
    private var url: String? = null
    private val extension: String? = originalFileName?.let { FilenameUtils.getExtension(it) }
    private var uploadedAt: LocalDateTime? = null
    fun onUploaded(url: String) {
        this.url = url
        this.uploadedAt = LocalDateTime.now()
    }

    fun buildPath(vararg path: String?): String {
        val basePath = path.filterNotNull().joinToString("/") { it }
        return "user:${user.id}/$basePath/${UUID.randomUUID()}.$extension"
    }
}