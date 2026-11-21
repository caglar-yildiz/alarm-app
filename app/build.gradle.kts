plugins {
    id("com.android.application") version "8.10.1"
    id("org.jetbrains.kotlin.android") version "2.0.21"
}

android {
    namespace = "com.caglaryildiz.alarm"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.caglaryildiz.alarm"
        minSdk = 26
        targetSdk = 36
        versionCode = 22
        versionName = "0.11.0"
    }
    bundle {
        // The in-app language picker needs every translation installed, not only the device language.
        language { enableSplit = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // The Android stub of org.json throws in local unit tests; use the real one there.
    testImplementation("org.json:json:20240303")
    implementation("com.android.billingclient:billing:8.0.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.android.gms:play-services-auth-blockstore:16.4.0")
    implementation("com.google.android.play:review:2.0.2")
}
