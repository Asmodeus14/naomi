/**
 * The privacy boundary, expressed as a build file.
 *
 * Plain Kotlin on purpose. No Android plugin means no manifest, so this module
 * cannot contribute a permission to the merged manifest; no dependency on
 * `:app` means the networked code below it has no type for a memory. Both
 * properties are enforced by the compiler rather than by review.
 *
 * Adding a dependency here — especially `project(":app")` or Room — would
 * quietly undo that. WebModuleBoundaryTest fails if anyone does.
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
