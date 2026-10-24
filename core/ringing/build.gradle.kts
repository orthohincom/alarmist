plugins {
    id("alarmist.android.library")
    id("alarmist.hilt")
}

android {
    namespace = "com.orthohin.alarmist.core.ringing"
    // android.util.Log in JVM tests.
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    api(project(":core:scheduling"))
    implementation(project(":core:sounds"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(testFixtures(project(":core:common")))
    testImplementation(testFixtures(project(":core:data")))
    testImplementation(libs.kotlinx.coroutines.test)
}
