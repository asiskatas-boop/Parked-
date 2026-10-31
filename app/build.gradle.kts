plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.parked.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.parked.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 19
        versionName = "0.8.0"
        // CARTO's free basemap key, from the CARTO_API_KEY repository secret
        // (kept out of the public code). Without it the map tiles carry a watermark.
        val cartoKey = (System.getenv("CARTO_API_KEY") ?: "").replace("\"", "")
        buildConfigField("String", "CARTO_KEY", "\"$cartoKey\"")
    }

    // CI provides a fixed debug key so every build installs over the previous
    // one. Locally, the usual ~/.android/debug.keystore is used.
    signingConfigs {
        getByName("debug") {
            val fixedKey = System.getenv("PARKED_DEBUG_KEYSTORE")?.let { file(it) }
            if (fixedKey != null && fixedKey.exists()) {
                storeFile = fixedKey
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    // The APK people install is the release build: same fixed key (so it updates
    // 0.7 and earlier builds in place) but not debuggable, so app data can't be
    // copied off the phone over USB debugging.
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
