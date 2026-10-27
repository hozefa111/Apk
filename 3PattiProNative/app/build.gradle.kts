import java.security.MessageDigest

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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Override with -PfirebasePageSize=... for measured dataset/device tuning.
        buildConfigField("int", "FIRESTORE_PAGE_SIZE", (providers.gradleProperty("firebasePageSize").orNull ?: "150").also {
            require(it.toInt() in 2..500) { "firebasePageSize must be 2–500" }
        })
        versionCode = 45
        versionName = "2.7.24"
    }
    testBuildType = "sandbox"
    buildFeatures { buildConfig = true }
    signingConfigs {
        if (listOf("THREE_PATTI_STORE_FILE", "THREE_PATTI_STORE_PASSWORD", "THREE_PATTI_KEY_ALIAS", "THREE_PATTI_KEY_PASSWORD").all { !System.getenv(it).isNullOrBlank() }) {
            create("stableRelease") {
                storeFile = file(System.getenv("THREE_PATTI_STORE_FILE"))
                storePassword = System.getenv("THREE_PATTI_STORE_PASSWORD")
                keyAlias = System.getenv("THREE_PATTI_KEY_ALIAS")
                keyPassword = System.getenv("THREE_PATTI_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        debug { buildConfigField("boolean", "USE_EMULATORS", "false") }
        create("sandbox") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".sandbox"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "USE_EMULATORS", "true")
        }
        release {
            signingConfig = signingConfigs.findByName("stableRelease")
            buildConfigField("boolean", "USE_EMULATORS", "false")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.webkit:webkit:1.12.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    doLast {
        check(android.signingConfigs.findByName("stableRelease") != null) {
            "Final release blocked: configure the stable THREE_PATTI_* signing variables. Debug signing is not a verified release."
        }
        check(android.signingConfigs.getByName("stableRelease").storeFile?.isFile == true) {
            "Final release blocked: the configured stable release keystore does not exist."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyReleaseSigning) }

val verifyKaliSharedAssets = tasks.register("verifyKaliSharedAssets") {
    val assetRoot = file("src/main/assets/kali")
    val authority = file("../tests/online-backend/functions/online/shared/engine.js")
    inputs.dir(assetRoot)
    inputs.file(authority)
    doLast {
        val sums = file("src/main/assets/kali/shared-assets.sha256")
        check(sums.isFile) { "Sync shared online assets from hozefa111/hh before building." }
        sums.readLines().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split("  ", limit = 2)
            check(parts.size == 2) { "Invalid shared-asset manifest" }
            val bytes = file("src/main/assets/kali/" + parts[1]).readBytes()
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
            check(actual == parts[0]) { "Stale online asset: " + parts[1] }
        }
        check(file("src/main/assets/kali/shared/engine.js").readBytes().contentEquals(authority.readBytes())) {
            "Android and authoritative backend rules differ. Sync from hozefa111/hh."
        }
    }
}
tasks.named("preBuild").configure { dependsOn(verifyKaliSharedAssets) }
