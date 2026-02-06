plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.kotlin.compose) // Keep this line!
}

android {
    namespace = "com.example.hapticbeat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.hapticbeat"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildFeatures {
        viewBinding = true
        // 'compose = true' is handled by 'alias(libs.plugins.kotlin.compose)'
    }
    // The entire 'composeOptions' block is handled by 'alias(libs.plugins.kotlin.compose)'
    // composeOptions {
    //     kotlinCompilerExtensionVersion = "1.5.2"
    // }
}

dependencies {
    // AndroidX Core & UI
    // Keep this at the top of Compose dependencies
    implementation(platform("androidx.compose:compose-bom:2024.06.00")) // Your current BOM version

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // ADDED: Explicitly include Compose Foundation for layout modifiers like fillMaxSize
    implementation("androidx.compose.foundation:foundation")

    androidTestImplementation ("androidx.compose.ui:ui-test-junit4:1.6.8") // Using explicit version

    implementation("com.github.wendykierp:JTransforms:3.1")

    // These are for traditional XML views, usually not needed if you're fully Compose for MainActivity
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.12.0")


    // Lifecycle extensions for ViewModelScope, etc.
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.0")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // RichTap SDK (Place RichTapSDK.aar in app/libs/directory)
    implementation(files("libs/RichTapSDK.aar"))

    // Greenrobot EventBus for communication between UI and Service
    implementation("org.greenrobot:eventbus:3.3.1")

    // TarsosDSP for FFT (using the official repository and coordinates)
    implementation("be.tarsos.dsp:core:2.5")
    implementation("be.tarsos.dsp:jvm:2.5")

    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Local broadcast manager
    implementation ("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    // REMOVED: This is now covered by androidx.compose.foundation:foundation
    // implementation("androidx.compose.foundation:foundation-layout")

}
