// 独立诗词功能模块：共享基础网络配置，Token 使用 DataStore，不依赖完整黄历或组件布局。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "lins.libs.module_poem"
    compileSdk = 37

    defaultConfig {
        minSdk = 31

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // consumer 规则随 AAR 交给最终应用；与本库自身的 proguardFiles 作用范围不同。
        consumerProguardFiles("consumer-rules.pro")
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
    // Java 源码/字节码目标保持为 11；运行 Gradle 的 JDK 由工具链文件另行指定。
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    // JVM 用例使用本机测试环境；androidTest 的执行则需要 Android 设备或模拟器。
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // 复用 Module_Base 提供的 Ktor 与序列化能力，避免再维护一套 HTTP 客户端。
    implementation(project(":Module_Base"))

    // 应用进程重启后仍可读取 Token；持久文件与键名定义在 PoemTokenStore.kt。
    implementation(libs.androidx.datastore.preferences)
}
