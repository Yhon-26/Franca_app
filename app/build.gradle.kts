plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val baseVersion = "0.2.24"
val deliveryBuildId = providers.gradleProperty("franca.buildId").orNull
require(deliveryBuildId == null || deliveryBuildId.matches(Regex("[a-f0-9]{12}"))) {
    "franca.buildId must have 12 lowercase hexadecimal characters"
}

android {
    namespace = "com.grafito.finanzas"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.grafito.finanzas"
        minSdk = 26
        // Raise after validating Android 16/17 behavior changes (see VALIDACION_MVP.md).
        targetSdk = 35
        versionCode = 26
        versionName = deliveryBuildId?.let { "$baseVersion+$it" } ?: baseVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release { isMinifyEnabled = false }
        create("validation") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".validation"
            matchingFallbacks += listOf("debug")
        }
    }
    testBuildType = "validation"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.named("androidTest") {
        assets.directories += "$projectDir/schemas"
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.3.0")
    implementation("androidx.graphics:graphics-path:1.1.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    implementation("androidx.work:work-runtime:2.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    // Tests resolve independently; give them a copy of the same platform constraints.
    androidTestImplementation(composeBom.copy())
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.7.0")
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }
