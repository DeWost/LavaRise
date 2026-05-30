plugins {
    java
    jacoco
    checkstyle
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.task)
    alias(libs.plugins.paperweight)
}

group = "dev.lavarise"
version = "1.7.2" // x-release-please-version
description = "Premium Rising Lava minigame plugin — 3 game modes, batch block engine, zero dependencies."

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

checkstyle {
    toolVersion = "10.21.1"
    configFile = file("config/checkstyle/checkstyle.xml")
    // Report style issues without breaking the build for now; the ruleset is
    // a curated starting point. Flip to false once the codebase is clean.
    isIgnoreFailures = true
    // Only the main sources — generated/test code is out of scope for style.
    sourceSets = listOf(project.sourceSets["main"])
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://jitpack.io")
}

dependencies {
    paperweight.paperDevBundle(libs.versions.paper.api.get())
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { // economy API only, never shaded
        exclude(group = "org.bukkit", module = "bukkit") // drop ancient transitive
    }
    implementation("org.bstats:bstats-bukkit:3.1.0") // shaded + relocated below
    // Adventure is bundled with Paper, no need to shade.
    // The MySQL driver is provided at runtime via plugin.yml `libraries:` —
    // MySqlStatsStorage only uses the JDK java.sql API, so no compile dep is needed.
    
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
        finalizedBy(jacocoTestReport)
    }

    jacocoTestReport {
        dependsOn(test)
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
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
        relocate("org.bstats", "dev.lavarise.libs.bstats") // avoid clashing with other plugins
        minimize {
            exclude(dependency("org.bstats:.*:.*")) // bStats loads classes reflectively
        }
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
