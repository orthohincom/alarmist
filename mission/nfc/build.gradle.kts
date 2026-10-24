plugins {
    id("alarmist.android.library")
    id("alarmist.android.compose")
    id("alarmist.hilt")
}

android {
    namespace = "com.orthohin.alarmist.mission.nfc"
}

dependencies {
    implementation(project(":mission:api"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
