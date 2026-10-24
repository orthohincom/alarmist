import java.util.Properties

plugins {
    id("alarmist.android.application")
    id("alarmist.android.compose")
    id("alarmist.hilt")
}

// Release signing, only if configured: keystore.properties (gitignored; storeFile, storePassword, keyAlias,
// keyPassword) or ALARMIST_* environment variables (CI). Without it the release APK stays unsigned, as F-Droid's
// build server and scripts/release-check.sh build it.
val signing: Map<String, String>? = run {
    val file = rootProject.file("keystore.properties")
    val values = if (file.exists()) {
        Properties().apply {
            file.inputStream().use(::load)
        }.let { p -> p.stringPropertyNames().associateWith(p::getProperty) }
    } else {
        mapOf(
            "storeFile" to System.getenv("ALARMIST_KEYSTORE"),
            "storePassword" to System.getenv("ALARMIST_KEYSTORE_PASSWORD"),
            "keyAlias" to System.getenv("ALARMIST_KEY_ALIAS"),
            "keyPassword" to System.getenv("ALARMIST_KEY_PASSWORD")
        ).filterValues { it != null }.mapValues { it.value!! }
    }
    values.takeIf { it.keys.containsAll(listOf("storeFile", "storePassword", "keyAlias", "keyPassword")) }
}

android {
    namespace = "com.orthohin.alarmist"
    defaultConfig {
        applicationId = "com.orthohin.alarmist"
        versionCode = 1
        versionName = "0.1.0"
    }
    signing?.let { values ->
        signingConfigs.create("release") {
            storeFile = rootProject.file(values.getValue("storeFile"))
            storePassword = values.getValue("storePassword")
            keyAlias = values.getValue("keyAlias")
            keyPassword = values.getValue("keyPassword")
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }
    // Lists the app's languages for Android 13+'s per-app language setting (default locale: res/resources.properties).
    androidResources { generateLocaleConfig = true }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:scheduling"))
    implementation(project(":core:ringing"))
    implementation(project(":feature:alarmedit"))
    implementation(project(":feature:alarmlist"))
    implementation(project(":feature:reliability"))
    implementation(project(":feature:ringing"))
    implementation(project(":feature:settings"))
    implementation(project(":mission:api"))
    implementation(project(":mission:barcode"))
    implementation(project(":mission:math"))
    implementation(project(":mission:memory"))
    implementation(project(":mission:nfc"))
    implementation(project(":mission:photo"))
    implementation(project(":mission:shake"))
    implementation(project(":mission:simon"))
    implementation(project(":mission:squats"))
    implementation(project(":mission:steps"))
    implementation(project(":mission:taporder"))
    implementation(project(":mission:typing"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
}
