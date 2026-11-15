// Align AGP built-in Kotlin with the Compose and serialization plugins.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}
plugins {
    alias(libs.plugins.android.application) apply false
}