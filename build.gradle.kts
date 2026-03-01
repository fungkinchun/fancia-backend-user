plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    kotlin("plugin.jpa") version "2.2.0"
    kotlin("kapt") version "2.0.21"
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.fancia.backend"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

fun RepositoryHandler.codeArtifactRepo(repoName: String) {
    maven {
        val baseUrl = System.getenv("ARTIFACT_REPO_URL") ?: project.findProperty("ARTIFACT_REPO_URL") as String? ?: error("ARTIFACT_REPO_URL must be provided via environment variable or project property")
        url = uri("$baseUrl/$repoName/")
        credentials {
            username = System.getenv("ARTIFACT_REPO_USER") ?: project.findProperty("ARTIFACT_REPO_USER") as String? ?: error("ARTIFACT_REPO_USER must be provided")
            password = System.getenv("ARTIFACT_REPO_PASSWORD") ?: project.findProperty("ARTIFACT_REPO_PASSWORD") as String? ?: error("ARTIFACT_REPO_PASSWORD must be provided")
        }
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/snapshot") }
    codeArtifactRepo("fancia-backend-shared-common")
    codeArtifactRepo("fancia-backend-shared-user")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-quartz")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.postgresql:postgresql")
    implementation("org.mapstruct:mapstruct:1.6.3")
    kapt("org.mapstruct:mapstruct-processor:1.6.3")
    implementation("org.apache.commons:commons-lang3:3.18.0")
    implementation("commons-io:commons-io:2.20.0")
    implementation("software.amazon.awssdk:s3:2.42.4")
    implementation("software.amazon.awssdk:sts:2.42.4")
    implementation("io.awspring.cloud:spring-cloud-aws-starter-secrets-manager:4.0.0")
    implementation("com.amazonaws.secretsmanager:aws-secretsmanager-jdbc:2.0.4")
    implementation("com.fancia.backend.shared:common:0.0.1-SNAPSHOT")
    implementation("com.fancia.backend.shared:user:0.0.1-SNAPSHOT")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:3.0.1")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1")
    implementation("org.springdoc:springdoc-openapi-starter-common:3.0.1")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}