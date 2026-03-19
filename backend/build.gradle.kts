plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.jpa") version "2.3.0"
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("plugin.spring") version "2.3.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.7"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "no.battlefront.balancer"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
    jvmToolchain(25)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("com.h2database:h2")
    implementation(kotlin("stdlib"))
}

tasks.test {
    useJUnitPlatform()
}

// Kover: code coverage for Kotlin tests
kover {
    // Keep default settings; adjust later if you want specific filters/thresholds
}

// ktlint: Kotlin code style checks
ktlint {
    verbose.set(true)
    android.set(false)
    // You can add an .editorconfig later for project-specific rules
}

tasks.check {
    // Ensure style checks and tests run as part of the standard verification lifecycle
    dependsOn("ktlintCheck")
}

// Load backend/.env into environment for bootRun (Spring does not load .env by itself)
tasks.named<JavaExec>("bootRun") {
    val envFile = file("${layout.projectDirectory.asFile}/.env")
    if (envFile.exists()) {
        envFile
            .readLines()
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .forEach { line ->
                val eq = line.indexOf('=')
                if (eq > 0) {
                    val key = line.substring(0, eq).trim()
                    var value = line.substring(eq + 1).trim()
                    if ((value.startsWith('"') && value.endsWith('"')) ||
                        (value.startsWith("'") && value.endsWith("'"))
                    ) {
                        value = value.drop(1).dropLast(1)
                    }
                    environment(key, value)
                }
            }
    }
}
