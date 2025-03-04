import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.jetbrains.gradle.ext.runConfigurations
import org.jetbrains.gradle.ext.settings
import org.jetbrains.gradle.ext.Gradle

plugins {
    id("com.gtnewhorizons.retrofuturagradle") version "1.4.1"
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.9"
    id("com.github.gmazzo.buildconfig") version "5.5.1"
    id("io.freefair.lombok") version "8.12.2.1"
}

group = "dev.redstudio"
version = "2.0" // Versioning must follow the Ragnarök versioning convention: https://github.com/Red-Studio-Ragnarok/Commons/blob/main/Ragnar%C3%B6k%20Versioning%20Convention.md

val id = "rcw"
val minecraftVersion = "1.12.2"

minecraft {
    mcVersion = minecraftVersion
    username = "Desoroxxx"
    extraRunJvmArguments = listOf("-Dforge.logging.console.level=debug")
}

buildConfig {
    packageName("${project.group}.${id}")
    className("ProjectConstants")
    documentation.set("This class defines constants for ${project.name}.\n<p>\nThey are automatically updated by Gradle.")

    useJavaOutput()
    buildConfigField("String", "ID", provider { """"$id"""" })
    buildConfigField("String", "NAME", provider { """"${project.name}"""" })
    buildConfigField("String", "VERSION", provider { """"${project.version}"""" })
    buildConfigField("org.apache.logging.log4j.Logger", "LOGGER", "org.apache.logging.log4j.LogManager.getLogger(NAME)")
}

// Set the toolchain version to decouple the Java we run Gradle with from the Java used to compile and run the mod
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
    withSourcesJar() // Generate sources jar
}

tasks {
    processResources {
        val expandProperties = mapOf(
            "version" to project.version,
            "name" to project.name,
            "id" to id
        )

        inputs.properties(expandProperties)

        filesMatching("**/*.*") {
            if (!path.endsWith(".png"))
                expand(expandProperties)
        }

        finalizedBy("packageResourcePacks")
    }

    // TODO: Maybe this could be it's own task that also expands like above?
    //  this might fix the issue where it doesn't run on a clean build,
    //  and it might also allow us to exclude the resource packs from the jar

    register<Zip>("packageResourcePacks") {
        group = "build"
        description = "Packs resource packs into zip files and places them in the `build/libs` directory."
        destinationDirectory.set(layout.buildDirectory.dir("libs"))

        // Use the *processed resources*
        val resourcePacksDir = layout.buildDirectory.dir("resources/main/resourcepacks").get().asFile
        inputs.dir(resourcePacksDir)
        resourcePacksDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            from(dir)

            // Transform the folder name, replace underscores and capitalize words
            val resourcePackName = dir.name
                .split("_")
                .joinToString(" ") { it -> it.replaceFirstChar { it.uppercase() } }

            archiveFileName.set("${project.name} [$minecraftVersion] ${project.version} $resourcePackName.zip")
        }
    }

    withType<Jar>().configureEach {
        archiveBaseName.set(archiveBaseName.get().replace(" ", "-") + "-[$minecraftVersion]")
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.isFork = true
        options.forkOptions.jvmArgs = listOf("-Xmx4G")
    }
}

idea {
    module {
        inheritOutputDirs = true
        excludeDirs.addAll(setOf(".github", ".gradle", ".idea", "build", "gradle", "run").map(::file))
    }

    project {
        settings {
            jdkName = "1.8"
            languageLevel = IdeaLanguageLevel("JDK_1_8")

            runConfigurations {
                listOf("Client", "Server", "Obfuscated Client", "Obfuscated Server", "Vanilla Client", "Vanilla Server").forEach { name ->
                    create(name, Gradle::class.java) {
                        val prefix = name.substringBefore(" ").let { if (it == "Obfuscated") "Obf" else it }
                        val suffix = name.substringAfter(" ").takeIf { it != prefix } ?: ""
                        taskNames = setOf("run$prefix$suffix")
                    }
                }
            }
        }
    }
}
