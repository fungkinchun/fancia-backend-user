package com.fancia.backend.user.config

import org.quartz.Scheduler
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.quartz.SchedulerFactoryBean

@Configuration
class QuartzConfig(
    private val beanFactory: AutowireCapableBeanFactory
) {
    @Bean
    fun jobFactory(): AutowiringSpringBeanJobFactory {
        return AutowiringSpringBeanJobFactory(beanFactory)
    }

    @Bean
    fun schedulerFactoryBean(jobFactory: AutowiringSpringBeanJobFactory): SchedulerFactoryBean {
        val factory = SchedulerFactoryBean()
        factory.setJobFactory(jobFactory)
        return factory
    }

    @Bean
    fun scheduler(schedulerFactoryBean: SchedulerFactoryBean): Scheduler {
        return schedulerFactoryBean.scheduler
    }
}