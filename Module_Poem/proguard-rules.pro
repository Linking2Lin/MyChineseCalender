# 本库自身的压缩规则文件；当前 build.gradle.kts 中库 Release 的 isMinifyEnabled 为 false。
# 本文件不是 consumer 规则，不应据此推断最终 app 的 R8 保留范围。
# Proguard rules for Module_Poem
-keep class lins.libs.module_poem.model.** { *; }
