pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "Alarmist"

include(":app")
include(":core:common")
include(":core:designsystem")
include(":core:data")
include(":core:scheduling")
include(":core:model")
include(":core:ringing")
include(":core:sounds")
include(":feature:alarmedit")
include(":feature:alarmlist")
include(":feature:reliability")
include(":feature:settings")
include(":feature:ringing")
include(":mission:api")
include(":mission:barcode")
include(":mission:camera")
include(":mission:math")
include(":mission:memory")
include(":mission:nfc")
include(":mission:photo")
include(":mission:shake")
include(":mission:simon")
include(":mission:squats")
include(":mission:steps")
include(":mission:taporder")
include(":mission:typing")
