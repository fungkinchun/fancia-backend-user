package com.fancia.backend.user.core.repository

import com.fancia.backend.shared.common.moderation.core.entity.Report
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ReportRepository : JpaRepository<Report, UUID>
