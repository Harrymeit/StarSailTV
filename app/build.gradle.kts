import java.util.Properties

plugins {
    id("com.android.application")
}

// 本机私有默认值（local.properties 不入库）。开源仓库里不含任何真实地址或密码。
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.harrymeit.starsailtv"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.harrymeit.starsailtv"
        // 23 = Android 6.0：Media3 的下限之上，老盒子都能装
        minSdk = 23
        targetSdk = 37
        versionCode = 10001
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }

    // 加 -Pdpadtv.release=true 构建就不带"测试版"字样；平时构建默认是测试版
    defaultConfig {
        buildConfigField(
            "boolean",
            "TEST_BUILD",
            if (project.findProperty("dpadtv.release") == "true") "false" else "true",
        )
    }

    defaultConfig {
        buildConfigField("String", "DEFAULT_URL", "\"${localProps.getProperty("dpadtv.defaultUrl") ?: ""}\"")
        buildConfigField("String", "DEFAULT_PASSWORD", "\"${localProps.getProperty("dpadtv.defaultPassword") ?: ""}\"")
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("com.belerweb:pinyin4j:2.5.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("org.robolectric:robolectric:4.17")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // 原生播放：ExoPlayer 对 HLS 的容错远好于 MediaPlayer，H.265 取决于 SoC 硬解
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    // 二维码生成（纯 Java，仅 core，不含 Android 集成包）
    implementation("com.google.zxing:core:3.5.3")
}
