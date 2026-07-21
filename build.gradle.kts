plugins {
    id("net.fabricmc.fabric-loom") version "1.16-SNAPSHOT"
}

base {
    archivesName.set(properties["archives_base_name"] as String)
    version = libs.versions.mod.version.get()
    group = properties["maven_group"] as String
}

repositories {
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
    maven {
        name = "Meteor Dev Releases"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "Meteor Dev Snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    maven {
        name = "JitPack"
        url = uri("https://jitpack.io")
    }
}

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)

    compileOnly(libs.meteor.client)

    compileOnly( libs.xaeroplus)
    compileOnly( libs.xaeros.worldmap)
    compileOnly( libs.xaeros.minimap)
    compileOnly(libs.lambda.events)
    compileOnly(libs.caffeine)
    compileOnly( libs.baritone)
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to libs.versions.minecraft.get()
        )

        inputs.properties(propertyMap)
        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }

        from(configurations.runtimeClasspath.get().filter { it.name.startsWith("caffeine") })
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(25)
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }
}

loom {
    mods {
        create("oma") {
            sourceSet(sourceSets["main"])
        }
    }
}