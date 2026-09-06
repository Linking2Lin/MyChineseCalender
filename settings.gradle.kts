// 插件仓库与普通依赖仓库分别配置，排查下载故障时先确认缺少的是哪一类组件。
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    // 解析构建 JDK 工具链；实际 daemon 要求见 gradle/gradle-daemon-jvm.properties。
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    // 依赖仓库集中管理，模块不得私自声明仓库；依赖版本集中在版本目录文件中。
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// 依赖方向：app → Appwidget / Module_Poem / Module_Base；两个功能库 → Module_Base。
rootProject.name = "MyChineseCalendar"
include(":app")
include(":Appwidget")
include(":Module_Base")
include(":Module_Poem")
