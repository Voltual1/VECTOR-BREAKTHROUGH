import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "me.voltual.vb.library" 
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

}

dependencies {
    // 兼容低版本 Java 8 特性脱糖
    coreLibraryDesugaring(libs.android.desugar)
  implementation(libs.libsu.core)
}