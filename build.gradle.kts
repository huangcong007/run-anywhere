plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.17.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

val arthasVersion = "4.0.5"
val arthasJarRelativePath = "src/main/resources/arthas/arthas-boot.jar"

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")

    intellijPlatform {
        intellijIdea("2026.1")
        bundledPlugin("com.intellij.java")
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        group = providers.gradleProperty("pluginGroup")

        ideaVersion {
            sinceBuild = "261"
        }
    }
}

tasks.register("downloadArthasBoot") {
    group = "build setup"
    description = "Download arthas-boot.jar into plugin resources if missing"
    val target = layout.projectDirectory.file(arthasJarRelativePath)
    outputs.file(target)

    doLast {
        val targetFile = target.asFile
        if (targetFile.exists() && targetFile.length() > 0) {
            logger.lifecycle("arthas-boot.jar already exists at $targetFile")
            return@doLast
        }
        targetFile.parentFile.mkdirs()
        val url = uri(
            "https://repo1.maven.org/maven2/com/taobao/arthas/arthas-boot/" +
                "$arthasVersion/arthas-boot-$arthasVersion.jar"
        ).toURL()
        logger.lifecycle("Downloading arthas-boot.jar from $url")
        url.openStream().use { input ->
            targetFile.outputStream().use { output -> input.copyTo(output) }
        }
        logger.lifecycle("Downloaded arthas-boot.jar to $targetFile")
    }
}

tasks.named("processResources") {
    dependsOn("downloadArthasBoot")
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
        options.encoding = "UTF-8"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    test {
        useJUnitPlatform()
    }
}
