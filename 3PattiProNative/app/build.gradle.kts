plugins {
    id("com.android.application")
}
android {
    namespace = "com.hozefa.threepattipro.nativeapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hozefa.threepattipro.nativeapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0-native"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.core:core:1.15.0")
}
