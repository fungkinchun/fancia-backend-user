package com.fancia.backend.user.config

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

@Component
class ApplicationContextProvider : ApplicationContextAware {
    companion object {
        private lateinit var context: ApplicationContext
        fun getApplicationContext(): ApplicationContext = context
    }

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        context = applicationContext
    }
}