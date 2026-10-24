import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Android app module. Flavors: `fdroid` (everything, incl. Play-restricted features) and `play`.
 * Kotlin support is built into AGP 9, so no Kotlin plugin is applied.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            compileSdk = libs.int("compileSdk")
            defaultConfig {
                minSdk = libs.int("minSdk")
                targetSdk = libs.int("targetSdk")
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            flavorDimensions += "distribution"
            productFlavors {
                create("fdroid") { dimension = "distribution" }
                create("play") { dimension = "distribution" }
            }
            buildTypes {
                debug {
                    applicationIdSuffix = ".debug"
                    versionNameSuffix = "-debug"
                }
                release {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                    // No git commit hash in the APK: the public repository is an export with its own history, and
                    // the APK must not depend on which repository it was built from (reproducible builds).
                    vcsInfo { include = false }
                }
            }
            // F-Droid rejects the Google-encrypted dependency metadata block.
            dependenciesInfo {
                includeInApk = false
                includeInBundle = false
            }
            buildFeatures { buildConfig = true }
            configureCompileOptions(compileOptions)
            configureLint(lint)
        }
        configureTestLogging()
        dependencies { add("testImplementation", libs.findLibrary("junit").get()) }
        registerQuickCheck("assembleFdroidDebug", "testFdroidDebugUnitTest", "lintFdroidDebug")
    }
}
