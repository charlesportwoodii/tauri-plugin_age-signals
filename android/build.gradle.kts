plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.charlesportwoodii.tauri.plugin.agesignals"
    compileSdk = 36

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // Play Age Signals SDK
    implementation("com.google.android.play:age-signals:0.0.3")

    // Unit testing — runs locally with ./gradlew test, no device required
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.android.play:age-signals:0.0.3")

    implementation(project(":tauri-android"))
}
