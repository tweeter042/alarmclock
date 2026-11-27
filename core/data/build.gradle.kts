plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.tweeter042.alarmclock.core.data"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.room.testing)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

// The changelog shipped in :app's assets is parsed by ChangelogRepositoryTest, so a broken
// changelog.json fails this module's tests (and re-runs them whenever the file changes).
val changelogAsset = rootProject.file("app/src/main/assets/changelog.json")
tasks.withType<Test>().configureEach {
    inputs.file(changelogAsset).withPropertyName("changelogAsset")
    systemProperty("alarmclock.changelogAsset", changelogAsset.absolutePath)
}
