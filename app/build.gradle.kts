plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "ro.weatherwindow"
    compileSdk = 35
    defaultConfig {
        applicationId = "ro.weatherwindow"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
