// 最终应用模块：负责 APK、发布压缩、页面 Compose 及所有库模块的集成。
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "lins.applications.mychinesecalender"
    compileSdk = 37

    defaultConfig {
        // 已安装应用的身份；修改 applicationId 会被系统视为另一个应用。
        applicationId = "lins.applications.mychinesecalender"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // 最终 APK 统一压缩。Glance/Worker 的持久化类名与反射入口见 proguard-rules.pro。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    // Java 源码/字节码目标保持为 11；运行 Gradle 的 JDK 由工具链文件另行指定。
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    // Compose 库由同一 BOM 对齐版本；具体选择哪些组件仍由下面的依赖条目决定。
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // 页面按生命周期收集 StateFlow，防止后台页面持续进行不必要的 UI 收集。
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(project(":Appwidget"))
    implementation(libs.glance)

    implementation(project(":Module_Base"))
    implementation(project(":Module_Poem"))

    // JVM 用例使用本机测试环境；androidTest 的执行则需要 Android 设备或模拟器。
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    // 交互式预览工具仅用于 Debug；正式发布不包含这部分调试依赖。
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.work.runtime.ktx)
}
