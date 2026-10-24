plugins {
    id("alarmist.android.library")
    id("alarmist.android.compose")
    id("alarmist.hilt")
}

android {
    namespace = "com.orthohin.alarmist.feature.alarmlist"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:ringing"))
    implementation(project(":core:scheduling"))
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(testFixtures(project(":core:common")))
}
