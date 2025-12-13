plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.antigravity.gpaytest"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.antigravity.payment"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    applicationVariants.all {
        outputs.map { it as com.android.build.gradle.api.ApkVariantOutput }.forEach { output ->
            output.outputFileName = "PaymentTest.apk"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    
    // GPay and Firebase
    implementation(libs.play.services.wallet)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

tasks.register<Exec>("zipApk") {
    val buildDir = project.layout.buildDirectory.get().asFile
    commandLine("bash", "-c", """
        cd "${buildDir}/outputs/apk/debug" && \
        zip -P 1234 -r "PaymentTest.zip" "PaymentTest.apk"
    """)
}

tasks.register<Exec>("syncApkToDrive") {
    dependsOn("zipApk")
    commandLine("bash", "-c", """
    rclone copy \
    "${project.layout.buildDirectory.get().asFile}/outputs/apk/debug/PaymentTest.zip" \
    "gdrive:Code/Build/GpayTest"
    """)
}

tasks.whenTaskAdded {
    if (this.name == "assembleDebug") {
        if (project.hasProperty("g")) {
            this.finalizedBy("syncApkToDrive")
        }
    }
}

