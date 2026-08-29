import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.naomi.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.naomi.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Was false, which made the proguardFiles below inert and shipped an
            // unshrunk ~19MB APK.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        // Lint is a build gate, not advisory: it is what caught the invalid
        // RemoteViews elements that would have crashed every widget at runtime.
        warningsAsErrors = false
        abortOnError = true
        // local.properties is machine-generated and gitignored; its Windows
        // paths are not something the project can or should "fix".
        disable += "PropertyEscape"
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// Room writes schema JSON here so migrations can be diffed and tested.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * Fails the build if the merged manifest grants network access.
 *
 * Naomi's central claim is that it cannot send your memories anywhere, and that
 * is only true if the *merged* manifest has no INTERNET permission. It is not
 * enough to leave it out of our own manifest: ML Kit's GenAI client pulls in
 * `transport-backend-cct`, Google's telemetry uploader, which declares INTERNET
 * and ACCESS_NETWORK_STATE. Manifest merging added both to the APK, so a build
 * that looked local shipped with full network access.
 *
 * `tools:node="remove"` in AndroidManifest.xml strips them. This task is what
 * stops the next dependency bump from quietly putting them back.
 */
val forbiddenPermissions = listOf(
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE"
)

androidComponents.onVariants { variant ->
    val checkTask = tasks.register("check${variant.name.replaceFirstChar { it.uppercase() }}HasNoNetworkPermission") {
        group = "verification"
        description = "Asserts the merged ${variant.name} manifest grants no network access."

        val manifests = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST)
        inputs.file(manifests)

        doLast {
            val text = manifests.get().asFile.readText()
            val found = forbiddenPermissions.filter { permission ->
                text.contains("""<uses-permission android:name="$permission"""")
            }
            if (found.isNotEmpty()) {
                throw GradleException(
                    """
                    Naomi's merged manifest grants network access: ${found.joinToString()}

                    A dependency has reintroduced it. Either remove that dependency or add
                    a matching `tools:node="remove"` entry to app/src/main/AndroidManifest.xml,
                    then confirm with:
                      aapt2 dump permissions <apk>

                    Do not weaken this check. The privacy claim in README.md depends on it.
                    """.trimIndent()
                )
            }
        }
    }

    // Hook into `check` so CI and `./gradlew check` both enforce it.
    tasks.named("check").configure { dependsOn(checkTask) }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // On-device understanding via Gemini Nano, executed by the platform's
    // AICore service. Adds no network capability — the app declares no
    // INTERNET permission.
    implementation(libs.mlkit.genai.prompt)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
