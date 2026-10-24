plugins {
    id("alarmist.android.library")
    id("alarmist.hilt")
}

android {
    namespace = "com.orthohin.alarmist.core.sounds"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.android)
}
