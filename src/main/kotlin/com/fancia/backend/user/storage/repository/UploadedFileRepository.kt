package com.fancia.backend.user.storage.repository

import com.fancia.backend.user.storage.entity.UploadedFile
import org.springframework.data.jpa.repository.JpaRepository

interface UploadedFileRepository : JpaRepository<UploadedFile, Long>