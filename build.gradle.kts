plugins {
    java
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.task)
    alias(libs.plugins.paperweight)
}

group = "dev.lavarise"
version = "1.1.0"
description = "Premium Rising Lava minigame plugin — 3 game modes, batch block engine, zero dependencies."

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    paperweight.paperDevBundle(libs.versions.paper.api.get())
    compileOnly("me.clip:placeholderapi:2.11.6")
    // Adventure is bundled with Paper, no need to shade
    
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    // Gradle 9 no longer provisions the JUnit Platform launcher automatically.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}

tasks {
    test {
        useJUnitPlatform()
    }

    processResources {
        val props = mapOf(
            "version" to project.version,
            "description" to project.description
        )
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    // The thin jar is redundant — shadowJar is the distributable artifact.
    jar {
        enabled = false
    }

    shadowJar {
        archiveClassifier.set("")
        minimize()
    }

    // Paper 1.20.5+ loads Mojang-mapped plugins natively, so we ship the
    // Mojang-mapped shaded jar directly and skip the legacy Spigot reobf step.
    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("1.21.11")
    }
}
