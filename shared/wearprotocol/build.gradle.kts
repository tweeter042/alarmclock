plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.tweeter042.alarmclock.shared.wear"
    defaultConfig { minSdk = 33 }
}

dependencies {
    api(project(":core:model"))
    testImplementation(libs.junit)
}
