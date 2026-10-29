plugins {
    id("com.android.application")
}

android {
    namespace = "com.elsewhere.location.xposed"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.elsewhere.location.xposed"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
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
    compileOnly(files("libs/api-82.jar"))
}
