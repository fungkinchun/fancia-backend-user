package com.fancia.backend.user.config

import org.quartz.spi.TriggerFiredBundle
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.scheduling.quartz.SpringBeanJobFactory
import org.springframework.stereotype.Component

@Component
class AutowiringSpringBeanJobFactory(
    private val beanFactory: AutowireCapableBeanFactory
) : SpringBeanJobFactory() {
    override fun createJobInstance(bundle: TriggerFiredBundle): Any {
        val job = super.createJobInstance(bundle)
        beanFactory.autowireBean(job)
        beanFactory.initializeBean(job, job::class.java.name)
        return job
    }
}