plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("kotlinx-serialization")
    id("kotlin-kapt")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = Build.namespacePrefix("tonkeeperx")
    compileSdk = Build.compileSdkVersion
    ndkVersion = Build.ndkVersion

    defaultConfig {
        minSdk = Build.minSdkVersion

        ndk {
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
            abiFilters.add("x86")
            abiFilters.add("x86_64")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
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
    implementation(libs.koin.core)
    implementation(libs.koin.workmanager)
    implementation(libs.kotlinX.datetime)
    implementation(libs.j2objc)
    implementation(libs.cbor)
    implementation(libs.ton.tvm)
    implementation(libs.ton.crypto)
    implementation(libs.ton.tlb)
    implementation(libs.ton.blockTlb)
    implementation(libs.ton.tonapiTl)
    implementation(libs.ton.contract)

    implementation(project(ProjectModules.Wallet.localization))
    implementation(project(ProjectModules.Wallet.api))

    implementation(project(ProjectModules.Wallet.Data.core))
    implementation(project(ProjectModules.Wallet.Data.tokens))
    implementation(project(ProjectModules.Wallet.Data.account))
    implementation(project(ProjectModules.Wallet.Data.settings))
    implementation(project(ProjectModules.Wallet.Data.rates))
    implementation(project(ProjectModules.Wallet.Data.collectibles))
    implementation(project(ProjectModules.Wallet.Data.events))
    implementation(project(ProjectModules.Wallet.Data.browser))
    implementation(project(ProjectModules.Wallet.Data.backup))
    implementation(project(ProjectModules.Wallet.Data.rn))
    implementation(project(ProjectModules.Wallet.Data.passcode))
    implementation(project(ProjectModules.Wallet.Data.staking))
    implementation(project(ProjectModules.Wallet.Data.purchase))
    implementation(project(ProjectModules.Wallet.Data.battery))
    implementation(project(ProjectModules.Wallet.Data.dapps))
    implementation(project(ProjectModules.Wallet.Data.contacts))
    implementation(project(ProjectModules.Wallet.Data.swap))

    implementation(project(ProjectModules.UIKit.core))
    implementation(project(ProjectModules.UIKit.flag))

    implementation(libs.androidX.core)
    implementation(libs.androidX.shortcuts)
    implementation(libs.androidX.appCompat)
    implementation(libs.androidX.activity)
    implementation(libs.androidX.fragment)
    implementation(libs.androidX.recyclerView)
    implementation(libs.androidX.viewPager2)
    implementation(libs.androidX.workManager)
    implementation(libs.androidX.biometric)
    implementation(libs.androidX.swiperefreshlayout)
    implementation(libs.androidX.lifecycle)
    implementation(libs.androidX.webkit)
    implementation(libs.androidX.browser)


    implementation(libs.material)
    implementation(libs.flexbox)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.config)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.performance)

    implementation(project(ProjectModules.Module.tonApi))
    implementation(project(ProjectModules.Module.blur))

    implementation(project(ProjectModules.Lib.network))
    implementation(project(ProjectModules.Lib.icu))
    implementation(project(ProjectModules.Lib.qr))
    implementation(project(ProjectModules.Lib.emoji))
    implementation(project(ProjectModules.Lib.security))
    implementation(project(ProjectModules.Lib.blockchain))
    implementation(project(ProjectModules.Lib.extensions))
    implementation(project(ProjectModules.Lib.ledger))
    implementation(project(ProjectModules.Lib.ur))
    implementation(project(ProjectModules.Lib.base64))

    implementation(libs.cameraX.base)
    implementation(libs.cameraX.core)
    implementation(libs.cameraX.lifecycle)
    implementation(libs.cameraX.view)

    implementation(libs.google.play.review)
    implementation(libs.google.play.billing)
    implementation(libs.google.play.update)
    implementation(libs.google.play.installreferrer)

    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.aptabase)

    implementation(libs.bleManager)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.foundationLayout)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.preview)
    debugImplementation(libs.compose.debugTooling)


    implementation(libs.compose.viewModel)
}