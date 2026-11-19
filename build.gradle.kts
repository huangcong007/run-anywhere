import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.20.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

val arthasVersion = "4.3.5"
val arthasJarRelativePath = "src/main/resources/arthas/arthas-boot.jar"
val arthasVersionMarkerRelativePath = "src/main/resources/arthas/arthas-boot.version"
val ARTHAS_BOOT_ENTRY = "arthas-boot.jar"

kotlin {
    // IntelliJ Platform 2026.2+ 要求 Java 25
    jvmToolchain(25)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
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
        intellijIdea("2026.3")
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
            // 仅在已验证的 2026.x 上声明兼容；新大版本发布后需重新验证再放开，
            // 避免 Marketplace 把未验证版本也算作"兼容"。
            untilBuild = "263.*"
        }
    }
}

tasks.register("downloadArthasBoot") {
    group = "build setup"
    description = "Download arthas-boot.jar into plugin resources if missing or outdated"
    val target = layout.projectDirectory.file(arthasJarRelativePath)
    val versionMarker = layout.projectDirectory.file(arthasVersionMarkerRelativePath)
    inputs.property("arthasVersion", arthasVersion)
    outputs.file(target)
    outputs.file(versionMarker)

    doLast {
        val targetFile = target.asFile
        val markerFile = versionMarker.asFile
        val isUpToDate = targetFile.exists() && targetFile.length() > 0 &&
            markerFile.exists() && markerFile.readText().trim() == arthasVersion
        if (isUpToDate) {
            logger.lifecycle("arthas-boot.jar $arthasVersion already exists at $targetFile")
            return@doLast
        }
        targetFile.parentFile.mkdirs()
        // Maven 上的 arthas-boot-<version>.jar 是瘦包（无 Main-Class），
        // 必须从发行包 arthas-bin.zip 中取可执行的 arthas-boot.jar
        val url = uri(
            "https://github.com/alibaba/arthas/releases/download/" +
                "arthas-all-$arthasVersion/arthas-bin.zip"
        ).toURL()
        logger.lifecycle("Downloading arthas-bin.zip $arthasVersion from $url")
        val partFile = targetFile.resolveSibling("${targetFile.name}.part")
        try {
            val connection = url.openConnection().apply {
                connectTimeout = 60_000
                readTimeout = 300_000
            }
            connection.getInputStream().use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name == ARTHAS_BOOT_ENTRY) {
                            partFile.outputStream().use { output -> zip.copyTo(output) }
                            break
                        }
                        entry = zip.nextEntry
                    }
                }
            }
            check(partFile.length() > 0) { "$ARTHAS_BOOT_ENTRY not found in $url" }
            Files.move(
                partFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            partFile.delete()
        }
        markerFile.writeText(arthasVersion)
        logger.lifecycle("Downloaded arthas-boot.jar $arthasVersion to $targetFile")
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn("downloadArthasBoot")
    // 仅用于本地判断是否需要重新下载，不打进插件包
    exclude("arthas/arthas-boot.version")
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "25"
        targetCompatibility = "25"
        options.encoding = "UTF-8"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        }
    }

    test {
        useJUnitPlatform()
    }
}
