import com.android.build.gradle.AppExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.android.baselineprofile)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.firebase.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.firebase.performance) apply false
}

allprojects {
    pluginManager.withPlugin("android") {
        configure<AppExtension> {
            signingConfigs {
                create("release") {
                    if (project.hasProperty("android.injected.signing.store.file")) {
                        storeFile = file(project.property("android.injected.signing.store.file").toString())
                        storePassword = project.property("android.injected.signing.store.password").toString()
                        keyAlias = project.property("android.injected.signing.key.alias").toString()
                        keyPassword = project.property("android.injected.signing.key.password").toString()
                    }
                }

                getByName("debug") {
                    storeFile = file("${project.rootDir.path}/${Signing.Debug.storeFile}")
                    storePassword = Signing.Debug.storePassword
                    keyAlias = Signing.Debug.keyAlias
                    keyPassword = Signing.Debug.keyPassword
                }
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(getLayout().buildDirectory)
}
