import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

/**
 * Shared Android configuration for every module.
 *
 * Modules only declare the plugins they need plus `android { namespace = ... }`.
 * Phone modules default to minSdk 34; modules shared with the Wear OS app
 * (core:model, shared:wearprotocol) lower it to 33 in their own build file.
 */
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

subprojects {
    pluginManager.withPlugin("com.android.library") {
        extensions.configure<LibraryExtension> {
            compileSdk {
                version = release(37) { minorApiLevel = 2 }
            }
            defaultConfig {
                minSdk = 34
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            testOptions {
                unitTests {
                    isIncludeAndroidResources = true
                    isReturnDefaultValues = true
                }
            }
            lint {
                abortOnError = false
                checkReleaseBuilds = false
                error += "MissingTranslation"
            }
            pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
                buildFeatures { compose = true }
            }
        }
    }
    pluginManager.withPlugin("com.android.application") {
        extensions.configure<ApplicationExtension> {
            compileSdk {
                version = release(37) { minorApiLevel = 2 }
            }
            defaultConfig {
                minSdk = 34
                targetSdk = 36
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            testOptions {
                unitTests {
                    isIncludeAndroidResources = true
                    isReturnDefaultValues = true
                }
            }
            lint {
                abortOnError = false
                checkReleaseBuilds = false
                error += "MissingTranslation"
            }
            pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
                buildFeatures { compose = true }
            }
        }
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
        dependencies {
            val bom = platform(catalog.findLibrary("compose-bom").get())
            "implementation"(bom)
            "testImplementation"(bom)
            "implementation"(catalog.findLibrary("compose-ui").get())
            "implementation"(catalog.findLibrary("compose-ui-graphics").get())
            "implementation"(catalog.findLibrary("compose-ui-tooling-preview").get())
            "implementation"(catalog.findLibrary("compose-foundation").get())
            "implementation"(catalog.findLibrary("compose-animation").get())
            "debugImplementation"(catalog.findLibrary("compose-ui-tooling").get())
        }
    }
    // Opt-ins used widely across the codebase so individual files do not need @OptIn noise.
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlinx.coroutines.FlowPreview",
                "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
                "-Xannotation-default-target=param-property",
            )
        }
    }
}
