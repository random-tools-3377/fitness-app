import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val appVersionName: String = providers.gradleProperty("appVersionName").get()

/** 1.2.3 becomes 1002003, so every new version installs over the one before it. */
fun versionCodeOf(name: String): Int {
    val parts = name.split(".").map { it.toInt() }
    require(parts.size == 3) { "appVersionName must look like 1.2.3, not $name" }
    return parts[0] * 1_000_000 + parts[1] * 1_000 + parts[2]
}

android {
    namespace = "io.github.randomtools3377.fitness"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.randomtools3377.fitness"
        minSdk = 26
        targetSdk = 35
        versionName = appVersionName
        versionCode = versionCodeOf(appVersionName)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // No signing config here on purpose. CI signs the release APK with apksigner in a
            // separate job that runs no build code, using the key from the repository secrets.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Nothing for app stores is added to the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    sourceSets {
        // The emulator tests open each committed database layout to check the migrations.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    // The database layout of every version is kept in app/schemas, so later versions can move
    // the user's data forward instead of losing it. CI fails if it is not committed.
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.core)
    // Barcode scanning (0.3.0): ZXing, Apache-2.0, works offline.
    implementation(libs.zxing.android.embedded)
    // The privacy lock (0.4.0) asks for the phone's own fingerprint, face or PIN.
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.compose.ui.test.manifest)
}
