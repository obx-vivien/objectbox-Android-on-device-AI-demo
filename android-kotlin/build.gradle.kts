buildscript {
    val objectboxVersion = "5.1.0"
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("io.objectbox:objectbox-gradle-plugin:$objectboxVersion")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.legacy.kapt) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
