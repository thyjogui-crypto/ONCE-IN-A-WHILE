plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val stableKeystorePath = System.getenv("ONCE_SIGNING_KEYSTORE")

android {
    namespace = "com.onceinawhile.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.onceinawhile.app"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("appVersionCode").orElse("2").get().toInt()
        versionName = providers.gradleProperty("appVersionName").orElse("1.0.1").get()
    }
    signingConfigs {
        if (!stableKeystorePath.isNullOrBlank()) {
            create("stable") {
                storeFile = file(stableKeystorePath)
                storePassword = System.getenv("ONCE_SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("ONCE_SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("ONCE_SIGNING_KEY_PASSWORD")
                storeType = "PKCS12"
            }
        }
    }
    buildTypes {
        getByName("debug") {
            if (!stableKeystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
        getByName("release") {
            isMinifyEnabled = false
            if (!stableKeystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.03.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.google.code.gson:gson:2.12.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
