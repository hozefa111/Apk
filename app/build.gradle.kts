plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.orbitplayer.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.orbitplayer.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "0.1.8"
    }
    // Release credentials are read only from private GitHub Actions secrets.
    val orbitSigningPath = System.getenv("ORBIT_KEYSTORE_PATH")
    val orbitSigningPassword = System.getenv("ORBIT_SIGN_PASSWORD")
    val canSignRelease = !orbitSigningPath.isNullOrBlank() && !orbitSigningPassword.isNullOrBlank()
    signingConfigs {
        if (canSignRelease) {
            create("orbitRelease") {
                storeFile = file(orbitSigningPath!!)
                storePassword = orbitSigningPassword
                keyAlias = "orbitplayer"
                keyPassword = orbitSigningPassword
                storeType = "PKCS12"
            }
        }
    }
    buildTypes {
        release {
            if (canSignRelease) signingConfig = signingConfigs.getByName("orbitRelease")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = true; buildConfig = true }
}

dependencies {
    val media3 = "1.11.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    testImplementation("junit:junit:4.13.2")
}
