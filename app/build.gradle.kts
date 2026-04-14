plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.platform.smartwastemanager"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.platform.smartwastemanager"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // ---- AndroidX Core ----
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // ---- Compose ----
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // ---- Navigation ----
    implementation(libs.androidx.navigation.compose)

    // ---- Firebase (BOM manages all Firebase versions) ----
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.messaging.ktx)

    // ---- Google Maps ----
    implementation(libs.maps.compose)
    implementation(libs.play.services.location)

    // ---- ML Kit ----
    implementation(libs.mlkit.image.labeling)

    // ---- CameraX ----
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // Provides ListenableFuture on the classpath — required by ProcessCameraProvider.getInstance()
    implementation(libs.androidx.concurrent.futures)

    // ---- Accompanist ----
    implementation(libs.accompanist.permissions)

    // ---- DataStore ----
    implementation(libs.androidx.datastore.preferences)

    // ---- Coil (image loading) ----
    implementation(libs.coil.compose)

    // ---- WorkManager ----
    implementation(libs.androidx.work.runtime.ktx)

    // ---- Splash Screen ----
    implementation(libs.androidx.core.splashscreen)

    // ---- Markdown Renderer ----
    implementation(libs.compose.markdown)

    // ---- Testing ----
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}