plugins {
    id("alarmist.android.library")
    id("alarmist.android.compose")
}

android {
    namespace = "com.orthohin.alarmist.mission.camera"
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
}
