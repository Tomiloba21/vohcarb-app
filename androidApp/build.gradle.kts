plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "app.vohcarb.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.vohcarb"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            // The emulator reaches the PC's localhost at 10.0.2.2
            buildConfigField("String", "DEFAULT_SERVER_URL", "\"http://10.0.2.2:8080\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // TODO: the production URL once the VPS and domain exist; until then it is typed on the sign-in screen
            buildConfigField("String", "DEFAULT_SERVER_URL", "\"\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
}
