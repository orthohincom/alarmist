import com.android.build.api.dsl.CompileOptions
import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.int(alias: String): Int = findVersion(alias).get().requiredVersion.toInt()

internal fun Project.javaVersion(): JavaVersion = JavaVersion.toVersion(libs.int("jvmTarget"))

internal fun Project.configureCompileOptions(options: CompileOptions) {
    options.sourceCompatibility = javaVersion()
    options.targetCompatibility = javaVersion()
}

internal fun configureLint(lint: Lint) {
    lint.abortOnError = true
    // Version bumps are a deliberate step, not a lint failure.
    lint.disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
}

/** `quickCheck` is what scripts/check.sh runs: one fast variant per module. */
internal fun Project.registerQuickCheck(vararg dependsOn: String) {
    tasks.register("quickCheck") {
        group = "verification"
        description = "Builds, unit-tests and lints one variant of this module."
        dependsOn(*dependsOn)
    }
}

internal fun Project.configureTestLogging() {
    tasks.withType<Test>().configureEach {
        testLogging {
            quiet {
                events(TestLogEvent.FAILED)
                exceptionFormat = TestExceptionFormat.SHORT
            }
            events(TestLogEvent.FAILED)
            exceptionFormat = TestExceptionFormat.SHORT
        }
    }
}
