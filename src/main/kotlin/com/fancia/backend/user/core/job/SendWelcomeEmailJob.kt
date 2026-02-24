package com.fancia.backend.user.core.job

import com.fancia.backend.user.config.ApplicationContextProvider
import com.fancia.backend.user.core.job.handler.SendWelcomeEmailJobHandler
import org.quartz.JobBuilder
import org.quartz.JobExecutionContext
import org.quartz.TriggerBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.quartz.QuartzJobBean
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import org.springframework.stereotype.Component
import java.util.*

@Component
class SendWelcomeEmailJob : QuartzJobBean() {
    @Autowired
    private lateinit var sendWelcomeEmailJobHandler: SendWelcomeEmailJobHandler
    override fun executeInternal(context: JobExecutionContext) {
        sendWelcomeEmailJobHandler.run(
            UUID.fromString(context.mergedJobDataMap.getString(USER_ID))
        )
    }

    companion object {
        const val USER_ID = "userId"
        const val JOB_GROUP = "sendWelcomeEmailJobGroup"
        fun getJobId(userId: UUID): String {
            return "sendWelcomeEmailJob-$userId"
        }

        fun getTriggerId(userId: UUID): String {
            return "sendWelcomeEmailTrigger-$userId"
        }

        fun scheduleJob(userId: UUID) {
            val schedulerFactory =
                ApplicationContextProvider.getApplicationContext().getBean(SchedulerFactoryBean::class.java)
            val jobId: String = getJobId(userId)
            val triggerId: String = getTriggerId(userId)
            val job = JobBuilder.newJob(SendWelcomeEmailJob::class.java)
                .withIdentity(jobId, JOB_GROUP)
                .usingJobData(USER_ID, userId.toString())
                .build()
            val trigger = TriggerBuilder.newTrigger().withIdentity(
                triggerId,
                JOB_GROUP
            )
                .startNow()
                .build()
            schedulerFactory.scheduler.scheduleJob(job, trigger)
            schedulerFactory.start()
        }
    }
}
