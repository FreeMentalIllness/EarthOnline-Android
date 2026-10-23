# ─────────────────────────────────────────────────────────────
# EarthOnline R8 混淆保留规则（v1.0.6 收尾：开启 minify）
# 关键原则：关闭 R8 优化（规避 AGP 8.5.x / R8 8.5.35 在类合并期的
# ConcurrentModificationException），仅做 shrink + obfuscate，
# 既瘦身又避免误删运行时必需的反射/原生类。
# ─────────────────────────────────────────────────────────────
-dontoptimize
-ignorewarnings

# 保留注解 / 泛型签名 / 内部类信息：kotlinx.serialization、Room、Hilt 反射都依赖
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeVisibleTypeAnnotations, AnnotationDefault
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ── 应用入口与 Hilt ──
-keep class com.example.earthonline.EarthOnlineApp { *; }
-keep class * implements dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}
# Hilt 生成的 *_Hilt* / hilt_aggregated_deps 等由 Hilt processor 自带规则保留；
# 这里兜底防止 R8 误删 ViewModel 子类
-keep class com.example.earthonline.ui.**.*ViewModel { *; }

# ── 本地离线推理引擎（llama.cpp / Inferkt + MNN）：JNI 与 KMP 绑定，必须整包保留 ──
-keep class com.dilivva.** { *; }
-keep class **.inferkt.** { *; }
-dontwarn com.dilivva.**
-dontwarn **.inferkt.**

# MNN 引擎 JNI 桥（移植自 messageAIHelper，包名被预编译 liblocalllm.so 符号锁定，
# 类名 / native 方法名一个字符都不能改，否则 JNI 解析失败 → 加载即崩）
-keep class com.example.qqaihelper.localllm.LocalLlmEngine { *; }
-dontwarn com.example.qqaihelper.**

# 原生方法声明所在类（防止 R8 重命名导致 JNI FindClass/GetMethodID 失败）
-keepclasseswithmembernames class * {
    native <methods>;
}

# ── 笔记富文本编辑器（richeditor-compose rc10）──
-keep class com.mohamedrejeb.richeditor.** { *; }
-dontwarn com.mohamedrejeb.**

# ── Room ──
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *
-keep class com.example.earthonline.data.local.entity.** { *; }

# ── 高德地图 SDK ──
-keep class com.amap.api.** { *; }
-keep class com.loc.** { *; }
-keep class com.autonavi.** { *; }
-keep class * implements java.io.Serializable
-dontwarn com.amap.api.location.**
-dontwarn com.autonavi.**

# ── 网络（Retrofit / OkHttp）──
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn okio.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.example.earthonline.data.network.** { *; }
-keep class com.example.earthonline.data.model.** { *; }

# ── kotlinx.serialization（@Serializable 实体保留字段名）──
-dontnote kotlinx.serialization.**
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# ── Coil 图片加载 ──
-dontwarn coil.**
-keep class coil.** { *; }

# ── WorkManager / 到期提醒 Worker（Hilt + 反射引用）──
-dontwarn androidx.work.**
-keep class com.example.earthonline.reminder.** { *; }

# ── Compose / Lifecycle（R8 误删告警抑制，规则由库自带，这里仅静音）──
-dontwarn androidx.compose.**
-dontwarn androidx.lifecycle.**
-dontwarn kotlinx.coroutines.**

# ── Kotlin 协程 / 反射基础 ──
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }
