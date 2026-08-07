import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
            // Re-export the inspector's public API so Swift can see KaptorIos, TransactionRepository,
            // KaptorMockRequest, KaptorRequestRerunner, etc.
            export(project(":kaptor-ui"))
            export(project(":kaptor-core"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kaptor-ui"))
            api(project(":kaptor-core"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
