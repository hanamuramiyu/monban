import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.GradleException
import java.util.zip.ZipFile

plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.6.1"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":presentation"))
    implementation(project(":config:file"))
    implementation(project(":storage:file"))
    implementation("org.bstats:bstats-velocity:3.2.1")

    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val pluginVersion = project.version.toString()
val generatedPluginMetadataDir = layout.buildDirectory.dir("generated/sources/plugin-metadata/java/main")
val distributionJar = layout.buildDirectory.file("libs/monban-velocity-${project.version}.jar")

val generatePluginMetadata by tasks.registering(Copy::class) {
    from("src/main/templates/MonbanVelocityPluginMetadata.java.template")
    into(generatedPluginMetadataDir.map { it.dir("hanamuramiyu/monban/velocity") })
    filteringCharset = "UTF-8"
    inputs.property("pluginVersion", pluginVersion)
    filter<ReplaceTokens>("tokens" to mapOf("version" to pluginVersion))
    rename { "MonbanVelocityPluginMetadata.java" }
}

sourceSets {
    main {
        java.srcDir(generatedPluginMetadataDir)
    }
}

val verifyDistributionJar by tasks.registering {
    group = "verification"
    description = "Verifies the Velocity distribution JAR packaging invariants."
    dependsOn("shadowJar")
    inputs.file(distributionJar)

    doLast {
        val jarFile = inputs.files.singleFile
        ZipFile(jarFile).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()

            fun count(path: String): Int = entries.count { it == path }

            if (entries.any { it.startsWith("org/bstats/") }) {
                throw GradleException("Velocity distribution contains unrelocated bStats classes.")
            }
            if (count("hanamuramiyu/monban/internal/libs/bstats/velocity/Metrics.class") != 1) {
                throw GradleException("Velocity distribution must contain relocated bStats exactly once.")
            }
            if (count("hanamuramiyu/monban/internal/libs/bstats/MetricsBase.class") != 1) {
                throw GradleException("Velocity distribution must contain relocated bStats-base exactly once.")
            }
            if (!entries.any {
                    it.startsWith("hanamuramiyu/monban/internal/libs/bstats/json/") && it.endsWith(".class")
                }) {
                throw GradleException("Velocity distribution must contain relocated bStats JSON support.")
            }
            if (count("META-INF/THIRD_PARTY_NOTICES.md") != 1) {
                throw GradleException("Velocity distribution must contain META-INF/THIRD_PARTY_NOTICES.md.")
            }
        }
    }
}


tasks {
    compileJava {
        dependsOn(generatePluginMetadata)
    }

    test {
        useJUnitPlatform()
    }

    processResources {
        from(rootProject.file("THIRD_PARTY_NOTICES.md")) {
            into("META-INF")
        }
    }

    jar {
        enabled = false
    }

    shadowJar {
        archiveFileName.set("monban-velocity-${project.version}.jar")
        relocate("org.yaml.snakeyaml", "hanamuramiyu.monban.internal.libs.snakeyaml")
        relocate("org.bstats", "hanamuramiyu.monban.internal.libs.bstats")
    }

    assemble {
        dependsOn(shadowJar)
    }

    check {
        dependsOn(verifyDistributionJar)
    }
}
