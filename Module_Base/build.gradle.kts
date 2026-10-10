// 基础库：共享网络、按日仓库、Room 模型/DAO/迁移和日志，不依赖上层页面或小组件。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.devtools.ksp)
}

android {
    namespace = "lins.libs.module_base"
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

    buildFeatures {
        // 共享客户端和业务日志从本模块 BuildConfig.DEBUG 选择输出级别。
        buildConfig = true
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

    // 日志框架（仅 Module_Base 内部使用）
    implementation(libs.com.elvishew.xlog)

    // Ktor 网络请求框架（上层模块直接使用 KtorClient.client 发请求，需要 api 透传）
    api(libs.ktor.client.core)
    api(libs.ktor.client.android)
    api(libs.ktor.client.content.negotiation)
    api(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.slf4j.android)

    // Room 数据库（上层模块直接使用 AppDataBase，其父类 RoomDatabase 需要可见）
    api(libs.room.runtime)
    api(libs.room.ktx)
    // DAO 实现和数据库结构检查由 Room 编译器生成，不直接编辑生成文件。
    ksp(libs.room.compiler)

    // Kotlinx JSON 序列化库（上层模块的 data class 使用 @Serializable 注解）
    api(libs.kotlinx.serialization.json)

    // Coroutines（上层模块使用协程调度）
    api(libs.kotlinx.coroutines.android)
}

ksp {
    // Schema 应纳入版本控制供审查迁移；由 Room/KSP 生成，不能用手工改 JSON 替代真实迁移。
    arg("room.schemaLocation", "$projectDir/schemas")
}
