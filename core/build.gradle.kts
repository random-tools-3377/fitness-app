import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The app's rules and logic, shared by Android and iPhone (Kotlin Multiplatform). Plain Kotlin
// with no Android or Apple code, so it is tested on any machine. Android uses the jvm target.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        // Runs the label-reading models for real when FITNESS_AI_MODELS points at them.
        jvmTest.dependencies {
            implementation(libs.onnxruntime.jvm)
        }
    }
}
