plugins {
    // PUBLIC_INTERFACE
    // Android application and Kotlin plugins for the app module.
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // PUBLIC_INTERFACE
    // Standard Android configuration for the Streamly app.
    namespace = "org.example.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.gradle.experimental.android.app"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
            // keep defaults
        }
    }

    compileOptions {
        // Kotlin/JVM target 17 as per settings default jdkVersion
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        // No Compose; use view system
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }
}

dependencies {
    // PUBLIC_INTERFACE
    // Application dependencies with explicit versions, no Compose.
    implementation("org.apache.commons:commons-text:1.11.0")

    implementation(project(":utilities"))

    // Material Components and AndroidX
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Networking and images
    implementation("io.coil-kt:coil:2.6.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ExoPlayer for media playback
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.19.1")

    // Unit testing (JUnit4 aligns with provided tests)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
