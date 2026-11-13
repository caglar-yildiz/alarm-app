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
        versionCode = 18
        versionName = "0.8.3"
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
    implementation("com.android.billingclient:billing:8.0.0")
    implementation("com.google.zxing:core:3.5.3")
}
