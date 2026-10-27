import community.flock.wirespec.plugin.Language
import community.flock.wirespec.plugin.gradle.CompileWirespecTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.wirespec)
}

group = "community.flock"
version = "0.0.1-SNAPSHOT"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.wirespec.runtime)
}

val wirespec = tasks.register<CompileWirespecTask>("wirespec") {
    description = "Generates Kotlin from the Wirespec contracts in src/main/wirespec"
    group = "wirespec"
    input = layout.projectDirectory.dir("src/main/wirespec")
    output = layout.buildDirectory.dir("generated/wirespec")
    packageName = "community.flock.library.contract"
    languages = listOf(Language.Kotlin)
}

// Compile the generated code with the rest of main; Gradle runs the wirespec task first
kotlin.sourceSets.main {
    kotlin.srcDir(wirespec.flatMap { it.output })
}
