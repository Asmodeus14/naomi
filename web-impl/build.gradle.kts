import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * The only module in this repository that can reach the network.
 *
 * It is an Android library so it can declare INTERNET in its own manifest, and
 * it is deliberately *not* a dependency of the default build — `:app` wires it
 * in only for the `connected` flavour. The offline flavour never sees it, which
 * is why `aapt2 dump permissions` on the default APK still shows no INTERNET.
 *
 * It depends on `:web-api` and nothing else of ours. It has no path to Room, to
 * the domain models, or to `:app`, so there is no memory here it could send.
 */
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.naomi.web.impl"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    api(project(":web-api"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
