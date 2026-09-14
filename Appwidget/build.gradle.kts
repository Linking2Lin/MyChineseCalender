// Glance 库模块：组件 UI、后台协调及日历接口装配；底层缓存契约和 Room 位于 Module_Base。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "lins.applications.appwidget"
    compileSdk = 37

    defaultConfig {
        minSdk = 31

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // consumer 规则随 AAR 交给最终应用；与本库自身的 proguardFiles 作用范围不同。
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            // 库本身不单独混淆，最终由 app 的 Release R8 统一处理依赖与调用关系。
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
    implementation(project(":Module_Base"))
    // JVM 用例使用本机测试环境；androidTest 的执行则需要 Android 设备或模拟器。
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Glance 组合最终生成桌面宿主使用的 RemoteViews，不复用 app 的 Compose 页面主题。
    implementation(libs.glance)
    implementation(libs.glance.material3)

    // 仅 src/debug 中的预览可引用；不要把 Preview 注解放到依赖它们的 main 源码。
    debugImplementation(libs.androidx.glance.preview)
    debugImplementation(libs.androidx.glance.appwidget.preview)

    // 即时与周期任务都由 WidgetScheduler 统一安排，任务入口为 SyncDateWorker。
    implementation(libs.androidx.work.runtime.ktx)


}
