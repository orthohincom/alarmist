plugins {
    id("alarmist.android.library")
    id("alarmist.android.compose")
    id("alarmist.hilt")
}

android {
    namespace = "com.orthohin.alarmist.feature.alarmedit"
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:ringing"))
    implementation(project(":core:sounds"))
    implementation(project(":mission:api"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(testFixtures(project(":core:common")))
}
