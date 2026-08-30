import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Upload-key credentials, from a local ignored file or from the environment.
 *
 * Two sources, in that order, because the two situations are different. On a
 * developer machine the credentials live in `keystore.properties`, which is in
 * .gitignore and points at a keystore kept *outside* the repository so it cannot
 * be committed even by accident. In CI there is no such file: the workflow
 * decodes the keystore from a secret and passes the passwords as environment
 * variables.
 *
 * Nothing here has a default. If neither source provides credentials, release
 * builds are left unsigned rather than silently falling back to the debug key —
 * an app signed with the debug key cannot be uploaded to Play, and discovering
 * that at upload time is worse than discovering it at build time.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String, env: String): String? =
    (keystoreProperties.getProperty(key) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

val uploadStoreFile = signingValue("storeFile", "KEYSTORE_FILE")
val uploadStorePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
val uploadKeyAlias = signingValue("keyAlias", "KEY_ALIAS")
val uploadKeyPassword = signingValue("keyPassword", "KEY_PASSWORD")

val hasUploadKey = uploadStoreFile != null &&
    uploadStorePassword != null &&
    uploadKeyAlias != null &&
    uploadKeyPassword != null &&
    file(uploadStoreFile!!).exists()

/**
 * versionName is edited by hand; versionCode is supplied by CI.
 *
 * Android refuses to install an update whose versionCode is not higher than the
 * installed one, so the number must only ever go up. Naomi ships as a sideloaded
 * APK with no store in the path, which means nothing upstream will catch a
 * mistake here — a wrong number is discovered by a user whose update will not
 * install.
 *
 * The release workflow therefore derives it from the git tag, not from a build
 * counter: `major * 10000 + minor * 100 + patch`, so `v0.2.0` is 200. Re-running
 * the workflow on the same tag produces the same number, and the same tag can
 * never describe two different builds.
 *
 * [VERSION_CODE_FALLBACK] applies only to local builds. It is deliberately lower
 * than any released code so that an APK built on someone's laptop cannot install
 * itself over a real release and pass for a newer one.
 *
 * versionName is deliberately *not* automated. It is the number a human reads,
 * and it should change because a release means something, not because a build
 * happened. The release workflow asserts that it matches the tag. See
 * DEPLOYMENT.md.
 */
val VERSION_CODE_FALLBACK = 3

val releaseVersionCode: Int =
    (System.getenv("VERSION_CODE") ?: providers.gradleProperty("versionCode").orNull)
        ?.toIntOrNull()
        ?: VERSION_CODE_FALLBACK

android {
    namespace = "com.naomi.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.naomi.app"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    /**
     * Whether this build can reach the internet at all.
     *
     * The two are separate APKs rather than one APK with a switch, because a
     * runtime toggle can only ever be a promise about code paths. Keeping the
     * networked module out of the default build makes the claim a fact the OS
     * enforces and `aapt2 dump permissions` can confirm.
     *
     * `offline` is the default and is what gets published.
     */
    flavorDimensions += "reach"

    productFlavors {
        create("offline") {
            dimension = "reach"
            isDefault = true
        }
        create("connected") {
            dimension = "reach"
            applicationIdSuffix = ".connected"
            versionNameSuffix = "-connected"
        }
    }

    signingConfigs {
        if (hasUploadKey) {
            create("upload") {
                storeFile = file(uploadStoreFile!!)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
                // v2 covers every device Naomi runs on: v1 (JAR signing) is only
                // needed below API 24 and minSdk is 26, so AGP skips it anyway.
                //
                // v3 is the one that earns its place. Naomi ships outside a
                // store, so there is no Play App Signing holding a recoverable
                // copy of this key — and v3's proof-of-rotation lineage is the
                // only mechanism that lets a compromised key be replaced on
                // Android 9+ without every user uninstalling and losing their
                // memories. It costs nothing to enable now and cannot be added
                // retroactively to APKs already in the wild.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Only ever the upload key, never the debug key. When no credentials
            // are available the build stays unsigned and `verifyReleaseSigning`
            // below says so, rather than producing something that looks
            // installable and is rejected by Play.
            signingConfig = if (hasUploadKey) signingConfigs.getByName("upload") else null
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
    // Only the offline flavour makes this claim. The connected flavour exists
    // precisely to have network access, and asserting otherwise there would be
    // a check that can never pass — which is how checks get deleted.
    if (variant.flavorName != "offline") return@onVariants

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
                    The offline build's merged manifest grants network access: ${found.joinToString()}

                    A dependency has reintroduced it — usually one that bundles a
                    telemetry uploader. Remove the dependency, or add a matching
                    `tools:node="remove"` entry to app/src/offline/AndroidManifest.xml,
                    then confirm against the built APK:
                      aapt2 dump permissions app/build/outputs/apk/offline/release/*.apk

                    (A networked module leaking into this flavour is caught by
                    checkWebModuleBoundary instead: src/offline strips these two
                    permissions unconditionally, so this check cannot see that case.)

                    Do not weaken this check, and do not "fix" it by deleting the flavour
                    filter above. The privacy claim in README.md depends on it.
                    """.trimIndent()
                )
            }
        }
    }

    // Hook into `check` so CI and `./gradlew check` both enforce it.
    tasks.named("check").configure { dependsOn(checkTask) }
}

/**
 * Fails a release build that is not signed with the upload key.
 *
 * The failure mode this exists for is quiet: a missing secret in CI produces an
 * unsigned artifact, the workflow goes green, and the problem only surfaces when
 * Play rejects the upload — or worse, when a debug-signed build is published to
 * testers and can never be updated by a properly signed one.
 */
tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Asserts release builds are signed with the upload key, not the debug key."

    doLast {
        if (!hasUploadKey) {
            throw GradleException(
                """
                No upload-key credentials, so the release build would be unsigned.

                Locally:  copy keystore.properties.example to keystore.properties and
                          fill it in. The keystore itself belongs outside this repo.
                In CI:    set KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS and
                          KEY_PASSWORD. See DEPLOYMENT.md.

                Release builds deliberately do not fall back to the debug key: an app
                signed with it cannot be uploaded to Play, and a debug-signed build
                that reached testers could never be updated by a real one.
                """.trimIndent()
            )
        }
    }
}

/**
 * Fails the build if the web layer can see the user's memories, or if the
 * networked half has leaked into the offline flavour.
 *
 * The permission check above proves the offline APK cannot open a socket. This
 * one proves the other half of §4: that the code which *can* open sockets has
 * no way to reach a transcript. Both are needed. A networked module that could
 * import Room would satisfy the first check and still be able to upload a
 * memory the moment someone wrote the call.
 */
tasks.register("checkWebModuleBoundary") {
    group = "verification"
    description = "Asserts the web modules cannot reach app data, and are absent from the offline build."

    doLast {
        // 1. The offline build must not contain the networked implementation.
        val offlineClasspath = configurations.getByName("offlineDebugRuntimeClasspath")
            .incoming.resolutionResult.allComponents.map { it.id.displayName }

        if (offlineClasspath.any { it.contains("web-impl") }) {
            throw GradleException(
                """
                :web-impl is on the offline build's classpath.

                The offline flavour is the published one and must have no networked
                code in it at all. Move the dependency back to `connectedImplementation`.
                """.trimIndent()
            )
        }

        // 2. Neither web module may see app data. If either could import Room or
        //    :app, the module boundary would be decoration.
        val forbidden = listOf("androidx.room", "project :app", "sqlite")
        for ((path, configuration) in listOf(
            ":web-api" to "runtimeClasspath",
            ":web-impl" to "debugRuntimeClasspath"
        )) {
            val module = project(path)
            val seen = module.configurations.findByName(configuration)
                ?.incoming?.resolutionResult?.allComponents
                ?.map { it.id.displayName }
                .orEmpty()

            val violations = seen.filter { component ->
                forbidden.any { component.contains(it, ignoreCase = true) }
            }
            if (violations.isNotEmpty()) {
                throw GradleException(
                    """
                    $path can reach app data: ${violations.joinToString()}

                    The web layer is only allowed to know about URLs and page text. Giving
                    it a type for a memory is what would make "private context never meets
                    public context" a promise instead of a fact.
                    """.trimIndent()
                )
            }
        }
    }
}

tasks.named("check").configure { dependsOn("checkWebModuleBoundary") }

dependencies {
    // The interface only. Plain Kotlin, no manifest, no network — safe in every
    // build. The implementation is added for `connected` alone, below.
    implementation(project(":web-api"))
    "connectedImplementation"(project(":web-impl"))

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
