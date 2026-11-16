import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.aubergine.ambit"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.aubergine.ambit"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2.0-beta1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        // Release signing reads from keystore.properties (gitignored) or AMBIT_* environment
        // variables. With neither present the release build is produced unsigned.
        create("release") {
            val props = Properties().apply {
                val f = rootProject.file("keystore.properties")
                if (f.exists()) f.inputStream().use { load(it) }
            }
            fun secret(key: String, env: String): String? =
                props.getProperty(key) ?: System.getenv(env)

            val storePath = secret("storeFile", "AMBIT_STORE_FILE")?.let { rootProject.file(it) }
            if (storePath != null && storePath.exists()) {
                storeFile = storePath
                storePassword = secret("storePassword", "AMBIT_STORE_PASSWORD")
                keyAlias = secret("keyAlias", "AMBIT_KEY_ALIAS")
                keyPassword = secret("keyPassword", "AMBIT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val rc = signingConfigs.getByName("release")
            if (rc.storeFile != null) signingConfig = rc
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    // Room's exported schemas double as instrumented migration-test fixtures.
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric reads the bundled OUI registry out of merged assets; without this
        // the asset manager sees an empty tree and the registry tests fail spuriously.
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    // Robolectric needs the Android test core to hand out an application Context.
    testImplementation(libs.androidx.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
