plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.legacy.kapt)
    alias(libs.plugins.kotlin.compose)
}

apply(plugin = "io.objectbox")

android {
    namespace = "com.screenshotsearcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.screenshotsearcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.01"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    flavorDimensions += "embedder"
    productFlavors {
        create("textOnly") {
            dimension = "embedder"
        }
        create("visionOnly") {
            dimension = "embedder"
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets", "../../shared/samples")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coroutines.android)
    implementation(libs.exifinterface)
    implementation(libs.mlkit.text)
    implementation(libs.play.services.tasks)
    add("textOnlyImplementation", libs.mediapipe.tasks.text)
    add("visionOnlyImplementation", libs.mediapipe.tasks.vision)

    implementation(libs.objectbox.kotlin)
    kapt(libs.objectbox.processor)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
