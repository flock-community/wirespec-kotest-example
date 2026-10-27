import community.flock.wirespec.integration.kotest.extension.KotestDslExtension
import community.flock.wirespec.integration.spring.extension.SpringMappingAnnotationsExtension
import community.flock.wirespec.plugin.Language
import community.flock.wirespec.plugin.gradle.CompileWirespecTask
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.wirespec)
}

group = "community.flock"
version = "0.0.1-SNAPSHOT"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.kotlinx.coroutines.reactor)
    implementation(kotlin("reflect"))

    implementation(libs.wirespec.runtime)
    implementation(libs.wirespec.spring)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.extensions.spring)
    testImplementation(libs.kotest.property)
    testImplementation(libs.wirespec.kotest)
    testImplementation(libs.wirespec.http)
}

tasks.test {
    useJUnitPlatform()
}

val wirespec = tasks.register<CompileWirespecTask>("wirespec") {
    description = "Generates Kotlin from the Wirespec contracts in src/main/wirespec"
    group = "wirespec"
    input = layout.projectDirectory.dir("src/main/wirespec")
    output = layout.buildDirectory.dir("generated/wirespec")
    packageName = "community.flock.library.contract"
    languages = listOf(Language.Kotlin)
    extensionClasses = listOf(
        // Puts @GetMapping, @PostMapping, ... on the generated Handler interfaces
        SpringMappingAnnotationsExtension::class.java,
        // Generates the Kotest scenario DSL into the `kotest` package, next to the models
        KotestDslExtension::class.java,
    )
}

// The DSL is test code: copy it out so it compiles with the tests and Kotest stays out of the jar
val wirespecKotestDsl = tasks.register<Sync>("wirespecKotestDsl") {
    from(wirespec.flatMap { it.output }) { include("**/contract/kotest/**") }
    into(layout.buildDirectory.dir("generated/wirespec-kotest"))
}

// Compile the generated code with the rest of main; Gradle runs the wirespec task first
kotlin.sourceSets {
    main {
        kotlin.srcDir(wirespec.flatMap { it.output })
        kotlin.exclude("**/contract/kotest/**")
    }
    test {
        kotlin.srcDir(wirespecKotestDsl)
    }
}
