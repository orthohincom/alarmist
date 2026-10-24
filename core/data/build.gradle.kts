plugins {
    id("alarmist.android.library")
    id("alarmist.hilt")
    id("alarmist.room")
}

android {
    namespace = "com.orthohin.alarmist.core.data"
    testFixtures { enable = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

// MigrationTestHelper reads the exported schemas as assets under Robolectric.
androidComponents {
    onVariants { variant ->
        variant.hostTests.values.forEach { it.sources.assets?.addStaticSourceDirectory("$projectDir/schemas") }
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(testFixtures(project(":core:common")))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
