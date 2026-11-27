plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.tweeter042.alarmclock.core.designsystem"

    testOptions {
        unitTests.all {
            // Roborazzi without its Gradle plugin: always record, PNGs go to build/screenshots-out/.
            it.systemProperty("roborazzi.test.record", "true")
            // Hardware-accelerated pixel copy so elevation and blur render like on a device.
            it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
            it.maxHeapSize = "3g"
        }
    }
}

dependencies {
    api(project(":core:model"))
    api(libs.compose.material3)
    api(libs.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    debugImplementation(libs.compose.ui.test.manifest)
}
