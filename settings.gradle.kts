pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AlarmClock"

include(":app")
include(":wear")
include(":core:model")
include(":core:data")
include(":core:alarm")
include(":core:designsystem")
include(":core:ui")
include(":shared:wearprotocol")
include(":feature:missions")
include(":feature:alarms")
include(":feature:ringing")
include(":feature:clock")
include(":feature:sleep")
include(":feature:integrations")
include(":feature:connected")
include(":feature:widgets")
include(":feature:settings")
