plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    kotlin("plugin.serialization")
}

android {
    namespace = Build.namespacePrefix("wallet.api")
    compileSdk = Build.compileSdkVersion

    defaultConfig {
        minSdk = Build.minSdkVersion
        consumerProguardFiles("consumer-rules.pro")
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
    implementation(Dependence.KotlinX.serialization)
    implementation(Dependence.KotlinX.serializationJSON)
    implementation(Dependence.Koin.core)
    implementation(Dependence.KotlinX.guava)
    implementation(project(Dependence.Module.tonApi))
    implementation(project(Dependence.Lib.network))
    implementation(project(Dependence.Lib.blockchain))
    implementation(project(Dependence.Lib.extensions))
    implementation(project(Dependence.Lib.icu))
    implementation(Dependence.GooglePlay.cronet)
    implementation(Dependence.Squareup.okhttp)
    implementation(Dependence.Squareup.sse)
}
