// 根构建脚本：仅声明插件版本，由各模块按需 apply
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // 强制新版 R8：AGP 8.5.2 自带 R8 8.5.35 在 minify 阶段存在
        // ConcurrentModificationException（shaking 期工具 bug，-dontoptimize 无法规避）。
        // 升级到 8.6.x 后该 bug 已修复，且兼容 AGP 8.5.x，可正常 shrink + obfuscate。
        classpath("com.android.tools:r8:8.6.27")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.compose.compiler) apply false
}
