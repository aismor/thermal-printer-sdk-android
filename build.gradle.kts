plugins {
    id("com.android.library") version "7.4.2"
    id("com.android.application") version "7.4.2" apply false
}

group = "io.github.aismor"
version = "1.0.0-SNAPSHOT"

android {
    namespace = "io.github.aismor.thermalprintersdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            consumerProguardFiles("consumer-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.7.1")
}
