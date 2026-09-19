import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun localOrEnvironment(name: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(name).orEmpty()

fun asBuildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "it.alantamanti.portshifttracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.alantamanti.portshifttracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 18
        versionName = "0.12.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "FIREBASE_API_KEY",
            asBuildConfigString(localOrEnvironment("PST_FIREBASE_API_KEY"))
        )
        buildConfigField(
            "String",
            "FIREBASE_APP_ID",
            asBuildConfigString(localOrEnvironment("PST_FIREBASE_APP_ID"))
        )
        buildConfigField(
            "String",
            "FIREBASE_PROJECT_ID",
            asBuildConfigString(localOrEnvironment("PST_FIREBASE_PROJECT_ID"))
        )
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            asBuildConfigString(localOrEnvironment("PST_GOOGLE_WEB_CLIENT_ID"))
        )
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("PST_KEYSTORE_FILE")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = File(keystorePath)
                storePassword = System.getenv("PST_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PST_KEY_ALIAS")
                keyPassword = System.getenv("PST_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (!System.getenv("PST_KEYSTORE_FILE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.04.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")

    val roomVersion = "2.8.5"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")

    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    testImplementation("junit:junit:4.13.2")
}
