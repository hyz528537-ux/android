plugins {
    id("com.tonapps.wallet.data")
    id("kotlin-parcelize")
}

android {
    namespace = Build.namespacePrefix("wallet.data.swap")
}

dependencies {
    implementation(Dependence.Koin.core)
    implementation(Dependence.KotlinX.guava)
    implementation(project(Dependence.Wallet.api))
    implementation(project(Dependence.Wallet.Data.core))
    implementation(project(Dependence.Lib.extensions))
    implementation(project(Dependence.Lib.icu))
}
