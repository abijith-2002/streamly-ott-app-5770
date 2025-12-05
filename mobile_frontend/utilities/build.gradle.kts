plugins {
    // PUBLIC_INTERFACE
    // Android library and Kotlin plugins for the utilities module.
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    // PUBLIC_INTERFACE
    // Android configuration for the utilities library module.
    namespace = "org.gradle.experimental.android.utilities"
    compileSdk = 34

    defaultConfig {
        minSdk = 30
        targetSdk = 34

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // defaults
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // PUBLIC_INTERFACE
    // Depend on the list module.
    api(project(":list"))

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
