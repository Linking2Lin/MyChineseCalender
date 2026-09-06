# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Glance/WorkManager 或已有点击动作可能保存这些完整类名，升级后的压缩不能重命名入口。
# 只保留类与框架创建所需构造器，其余可达实现仍由 R8 分析；移动类时须同时考虑历史数据兼容。
-keep class lins.applications.appwidget.MyAppWidget { public <init>(); }
-keep class lins.applications.appwidget.action.RefreshAction { public <init>(); }
-keep class lins.applications.appwidget.worker.SyncDateWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
