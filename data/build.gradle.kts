// Room dan implementasi repositori. Masih kerangka kosong; skema penyimpanan dirancang di tugas
// "Rancang model data dan skema penyimpanan lokal" (Tahap 2).
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.roziqrizal.rizqflow.data"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":domain"))
}
