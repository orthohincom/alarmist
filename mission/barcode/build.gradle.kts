plugins {
    id("alarmist.android.library")
    id("alarmist.android.compose")
    id("alarmist.hilt")
}

android {
    namespace = "com.orthohin.alarmist.mission.barcode"
}

dependencies {
    implementation(project(":mission:api"))
    implementation(project(":mission:camera"))
    implementation(libs.zxing.core)
}
