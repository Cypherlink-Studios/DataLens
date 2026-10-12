import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
@Suppress("DSL_SCOPE_VIOLATION")

plugins {
    java
    id("com.gradleup.shadow") version "8.3.8"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "dev.darkblade.datalens"
version = providers.gradleProperty("pluginVersion").getOrElse("1.0.0-SNAPSHOT")

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/groups/public/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-public/")
}

dependencies {
    // Spigot API — provided at runtime by the server (compatible with Bukkit, Spigot and Paper)
    compileOnly("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")

    // Bundled at runtime in Paper/Spigot — declare for IDE only
    compileOnly("org.yaml:snakeyaml:2.2")

    // Shaded libraries
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("de.tr7zw:item-nbt-api:2.15.7")

    // Adventure — shaded for cross-platform Component support
    // On Paper: native Adventure is used (zero overhead)
    // On Spigot: the platform adapter translates to legacy format
    implementation("net.kyori:adventure-platform-bukkit:4.4.1")
    implementation("net.kyori:adventure-text-serializer-legacy:4.17.0")

    // Tests
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 21
    }

    // Disable plain jar — only produce the shadow fat-jar
    jar {
        enabled = false
    }

    named<ShadowJar>("shadowJar") {
        archiveClassifier = ""
        archiveBaseName = "DataLens"

        relocate("de.tr7zw.changeme.nbtapi", "dev.darkblade.datalens.nbtapi")

        // Note: relocation disabled — shadow's ASM cannot process Java 21 bytecode (version 65).
        // Jackson, Caffeine and Adventure are NOT bundled by Spigot, so no classloader conflicts exist.
        // On Paper, the native Adventure takes precedence via adventure-platform-bukkit's detection.
        // Re-enable relocation once shadow ships an ASM version ≥ 9.7.

        manifest {
            attributes(
                "Implementation-Title" to "DataLens",
                "Implementation-Version" to project.version
            )
        }
    }

    // runServer task provided by run-paper plugin
    runServer {
        minecraftVersion("1.20.4")
    }

    test {
        useJUnitPlatform()
    }

    // Make 'build' produce the shadow jar
    build {
        dependsOn(shadowJar)
    }
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}