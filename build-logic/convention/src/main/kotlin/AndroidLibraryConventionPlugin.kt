import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Android library module. No flavors: flavor-specific code lives in `app`. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            compileSdk = libs.int("compileSdk")
            defaultConfig {
                minSdk = libs.int("minSdk")
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            configureCompileOptions(compileOptions)
            configureLint(lint)
        }
        configureTestLogging()
        dependencies { add("testImplementation", libs.findLibrary("junit").get()) }
        registerQuickCheck("testDebugUnitTest", "lintDebug")
    }
}
