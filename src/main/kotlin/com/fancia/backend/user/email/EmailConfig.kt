package com.fancia.backend.user.email

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import org.thymeleaf.templateresolver.ITemplateResolver

@Configuration
class EmailConfig {
    @Bean
    @Primary
    fun thymeleafTemplateResolver(): ITemplateResolver {
        val templateResolver = ClassLoaderTemplateResolver()
        templateResolver.prefix = "templates/"
        templateResolver.suffix = ".html"
        templateResolver.setTemplateMode("HTML")
        templateResolver.characterEncoding = "UTF-8"
        return templateResolver
    }

    @Bean
    fun thymeleafTemplateEngine(templateResolver: ITemplateResolver?): SpringTemplateEngine {
        val templateEngine = SpringTemplateEngine()
        templateEngine.setTemplateResolver(templateResolver)
        return templateEngine
    }
}
