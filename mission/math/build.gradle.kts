plugins {
    id("alarmist.android.library")
    id("alarmist.android.compose")
    id("alarmist.hilt")
}

android {
    namespace = "com.orthohin.alarmist.mission.math"
}

dependencies {
    implementation(project(":mission:api"))
}
