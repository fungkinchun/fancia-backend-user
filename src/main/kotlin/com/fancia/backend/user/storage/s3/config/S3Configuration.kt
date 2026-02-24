package com.fancia.backend.user.storage.s3.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "app.s3")
class S3Configuration {
    var bucketName: String? = null
    var region: String? = null
    var accessKey: String? = null
    var secretKey: String? = null
    var baseUrl: String? = null
    var storageClass: String? = null
}