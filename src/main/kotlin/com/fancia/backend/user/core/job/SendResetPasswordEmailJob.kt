package com.fancia.backend.user.core.job

import com.fancia.backend.user.core.job.handler.SendResetPasswordEmailJobHandler
import org.quartz.Job
import org.quartz.JobBuilder
import org.quartz.JobExecutionContext
import org.quartz.TriggerBuilder
import org.quartz.impl.StdSchedulerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.util.*

class SendResetPasswordEmailJob : Job {
    @Autowired
    private lateinit var sendResetPasswordEmailJobHandler: SendResetPasswordEmailJobHandler
    override fun execute(context: JobExecutionContext) {
        sendResetPasswordEmailJobHandler.run(
            UUID.fromString(context.mergedJobDataMap.getString(TOKEN_ID))
        )
    }

    companion object {
        const val TOKEN_ID = "tokenId"
        const val JOB_GROUP = "sendResetPasswordEmailJobGroup"
        fun getJobId(tokenId: UUID): String {
            return "sendResetPasswordEmailJob-$tokenId"
        }

        fun getTriggerId(tokenId: UUID): String {
            return "sendResetPasswordEmailTrigger-$tokenId"
        }

        fun scheduleJob(tokenId: UUID) {
            val jobId: String = getJobId(tokenId)
            val triggerId: String = getTriggerId(tokenId)
            val job = JobBuilder.newJob(SendResetPasswordEmailJob::class.java)
                .withIdentity(jobId, JOB_GROUP)
                .usingJobData(TOKEN_ID, tokenId.toString())
                .build()
            val trigger = TriggerBuilder.newTrigger().withIdentity(
                triggerId,
                JOB_GROUP
            )
                .startNow()
                .build()
            val scheduler = StdSchedulerFactory.getDefaultScheduler()
            scheduler.scheduleJob(job, trigger)
        }
    }
}