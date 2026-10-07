import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask

// Root build file for ai-elements-kotlin.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    // AGP 9.x built-in Kotlin: no `kotlin.android` plugin needed.
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.vanniktech.publish) apply false
    // API reference for every published module: ./gradlew :dokkaGenerate → build/dokka/html
    alias(libs.plugins.dokka)
    alias(libs.plugins.binary.compatibility.validator) apply false
}

/** Every published library (the BOM lists the same). */
val publishedModules = listOf(
    ":ai-elements-chat", ":ai-elements-core", ":ai-elements-ui", ":ai-elements-genui", ":ai-elements-mcp-apps", ":ai-elements-notifications", ":ai-elements-a2a", ":ai-elements-acp", ":ai-elements-koog", ":ai-elements-mermaid-native",
    ":harness:harness-core", ":harness:harness-filesystem", ":harness:harness-memory", ":harness:harness-planning",
    ":harness:harness-shell", ":harness:harness-sandbox-proot", ":harness:harness-browser", ":harness:harness-device",
    ":harness:harness-speech", ":harness:harness-scheduler",
)

subprojects {
    if (path in publishedModules) apply(plugin = "org.jetbrains.dokka")
    // One compile SDK for every Android module: `compileSdk` / `compileSdkMinor` in the version catalog.
    val sdk = rootProject.libs.versions.compileSdk.get().toInt()
    val minor = rootProject.libs.versions.compileSdkMinor.get().toInt()
    listOf("com.android.application", "com.android.library", "com.android.test").forEach { id ->
        pluginManager.withPlugin(id) {
            extensions.configure<com.android.build.api.dsl.CommonExtension>("android") {
                compileSdk { version = release(sdk) { minorApiLevel = minor } }
            }
        }
    }
}

dependencies {
    publishedModules.forEach { dokka(project(it)) }
}

dokka {
    moduleName.set("AI Elements for Kotlin")
}

// BCV's Android plugin discovery expects kotlin-android. AGP 9 uses built-in Kotlin instead.
// Wire the official BCV tasks to AGP's public release AAR artifact, not internal compiler paths.
// https://github.com/Kotlin/binary-compatibility-validator#validating-custom-jars
val apiCheck = tasks.register("apiCheck") { group = "verification" }
val apiDump = tasks.register("apiDump") { group = "verification" }
subprojects {
    if (path in publishedModules) {
        pluginManager.withPlugin("com.android.library") {
            val abiRuntime = configurations.create("abiRuntime") {
                isCanBeConsumed = false
                isCanBeResolved = true
            }
            dependencies.add(abiRuntime.name, rootProject.libs.kotlin.metadata.jvm)
            dependencies.add(abiRuntime.name, rootProject.libs.bcv.asm)
            dependencies.add(abiRuntime.name, rootProject.libs.bcv.asm.tree)
            extensions.configure<LibraryAndroidComponentsExtension> {
                onVariants(selector().withBuildType("release")) { variant ->
                    extensions.configure<org.jetbrains.dokka.gradle.DokkaExtension> {
                        dokkaSourceSets.register("release") {
                            displayName.set("Android")
                            sourceRoots.from(layout.projectDirectory.dir("src/main/kotlin"))
                            classpath.from(variant.compileConfiguration.incoming.artifactView {
                                attributes.attribute(org.gradle.api.attributes.Attribute.of("artifactType", String::class.java), "android-classes-jar")
                            }.files)
                            classpath.from(project.extensions.getByType<LibraryAndroidComponentsExtension>().sdkComponents.bootClasspath)
                            jdkVersion.set(17)
                            sourceLink {
                                localDirectory.set(layout.projectDirectory.dir("src/main/kotlin"))
                                remoteUrl("https://github.com/JuneLeGency/ai-elements-kotlin/blob/main/${project.path.trimStart(':').replace(':', '/')}/src/main/kotlin")
                                remoteLineSuffix.set("#L")
                            }
                        }
                    }
                    val aar = variant.artifacts.get(SingleArtifact.AAR)
                    val unpack = tasks.register<Sync>("extractApiClasses") {
                        from(aar.map { zipTree(it) }) { include("classes.jar") }
                        into(layout.buildDirectory.dir("api/classes"))
                    }
                    val dump = tasks.register<KotlinApiBuildTask>("apiBuild") {
                        dependsOn(unpack)
                        inputJar.set(layout.buildDirectory.file("api/classes/classes.jar"))
                        outputApiFile.set(layout.buildDirectory.file("api/${project.name}.api"))
                        runtimeClasspath.from(abiRuntime)
                    }
                    val check = tasks.register<KotlinApiCompareTask>("apiCheck") {
                        group = "verification"
                        projectApiFile.set(layout.projectDirectory.file("api/${project.name}.api"))
                        generatedApiFile.set(dump.flatMap { it.outputApiFile })
                    }
                    val update = tasks.register<Copy>("apiDump") {
                        group = "verification"
                        description = "Update the reviewed API baseline; never run automatically in CI."
                        from(dump.flatMap { it.outputApiFile })
                        into(layout.projectDirectory.dir("api"))
                    }
                    tasks.named("check") { dependsOn(check) }
                    apiCheck.configure { dependsOn(check) }
                    apiDump.configure { dependsOn(update) }
                }
            }
        }
    }
}
