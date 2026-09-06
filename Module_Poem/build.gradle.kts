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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // 复用 Module_Base 提供的 Ktor 与序列化能力，避免再维护一套 HTTP 客户端。
    implementation(project(":Module_Base"))

    // 跨进程重启保留诗词 Token，文件与键名定义在 PoemRepository。
    implementation(libs.androidx.datastore.preferences)
}
