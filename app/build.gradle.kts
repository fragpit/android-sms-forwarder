import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val signingKeystoreFile = rootProject.file("signing/sms-forwarder-debug.jks")
val signingChecksumFile = rootProject.file("signing/keystore.sha256")
val verifySigningKey = tasks.register("verifySigningKey") {
    val keystore = signingKeystoreFile
    val checksum = signingChecksumFile
    inputs.files(keystore, checksum)
    doLast {
        val expected = checksum.readText().trim()
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(keystore.readBytes())
            .joinToString("") { "%02x".format(it) }
        check(expected.matches(Regex("[0-9a-f]{64}")) && actual == expected) {
            "Signing key does not match signing/keystore.sha256. Restore the existing key."
        }
    }
}

tasks.matching { it.name == "validateSigningDebug" }.configureEach {
    dependsOn(verifySigningKey)
}

android {
    namespace = "dev.local.smsforwarder"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.local.smsforwarder"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField(
            "String",
            "BUILD_ID",
            "\"${providers.environmentVariable("SMS_FORWARDER_BUILD_ID").orElse("local").get()}\"",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("sideload") {
            storeFile = signingKeystoreFile
            storePassword = "android-sms-forwarder"
            keyAlias = "sms-forwarder"
            keyPassword = "android-sms-forwarder"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
