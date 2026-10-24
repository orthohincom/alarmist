plugins {
    id("alarmist.android.library")
    id("alarmist.hilt")
}

android {
    namespace = "com.orthohin.alarmist.core.scheduling"
    // android.util.Log in JVM tests.
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    api(project(":core:data"))

    testImplementation(testFixtures(project(":core:common")))
    testImplementation(testFixtures(project(":core:data")))
    testImplementation(libs.kotlinx.coroutines.test)
}
