import org.gradle.api.tasks.Sync

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val syncUiAssets = tasks.register<Sync>("syncUiAssets") {
    val source = rootProject.layout.projectDirectory.dir("ui")
    from(source) {
        include("logo.png", "logo2.png", "deezer-logo.png")
        include("fonts/Disco.ttf")
        include("artists/**", "singles/**", "music/covers/**")
    }
    into(layout.buildDirectory.dir("generated/uiAssets"))
}

val syncUiResources = tasks.register<Sync>("syncUiResources") {
    from(rootProject.layout.projectDirectory.file("ui/fonts/Disco.ttf"))
    into(layout.buildDirectory.dir("generated/uiRes/font"))
    rename { "disco.ttf" }
}

android {
    namespace = "com.sonar.app"
    compileSdk = 37
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "com.sonar.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/uiAssets").get().asFile)
    sourceSets["main"].res.srcDir(layout.buildDirectory.dir("generated/uiRes").get().asFile)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }
}

tasks.named("preBuild") {
    dependsOn(syncUiAssets)
    dependsOn(syncUiResources)
}

dependencies {
    implementation(project(":sonar-core"))

    implementation(platform("androidx.compose:compose-bom:2026.01.00"))
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.7")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.01.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
