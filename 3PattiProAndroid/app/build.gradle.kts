plugins {
    id("com.android.application")
}
android {
    namespace = "com.hozefa.threepattipro"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.hozefa.threepattipro.stable"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0.0"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.core:core:1.15.0")
}
