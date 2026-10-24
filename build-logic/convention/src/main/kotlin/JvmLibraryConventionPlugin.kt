import com.android.build.api.dsl.Lint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Pure Kotlin module (domain logic). No Android dependencies allowed. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("com.android.lint")

        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(libs.int("jvmTarget"))
        }
        extensions.configure<Lint> { configureLint(this) }
        configureTestLogging()
        dependencies { add("testImplementation", libs.findLibrary("junit").get()) }
        registerQuickCheck("test", "lint")
    }
}
