plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
}

import java.util.Properties

android {
    namespace = "com.example.earthonline"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.earthonline"
        minSdk = 24
        targetSdk = 35
        // v1.0.0：版本序列修正为「正式版自 1.0.0 起」；与设置页 AppInfo.version、Web 端
        // core.js 的 APP_INFO.version 三处必须一致（改一处要改三处）。
        // versionCode 只增不减（5=批次C，6=批次D，7=本轮修正：冷启动纯白起屏 + 原图导入，8=1.0.1 修复批：记账跳转容错 + 任务状态排版，9=v1.0.0 正式版重发布，10=v1.0.1，11=v1.0.2 主线：首页重构+地图闪退修复+头像壁纸裁剪+数据空状态骨架，12=v1.0.2 地图生命周期深度修复+全部动态页），
        // 否则已装机用户无法覆盖安装。versionName 回落 1.0.0 属「版本序列修正」，靠 versionCode 递增保证覆盖安装。
        // versionCode 19 = v1.0.3：高德引擎升 v10（根治新 Android 上足迹地图初始化崩溃）
        // + 主页视觉统一（卡片圆角/入口Tile自适应/动态按钮高对比）+ 列表重组性能优化
        // versionCode 20 = v1.0.3 补丁：深色模式开屏补齐 windowBackground（根治冷启动白闪）
        // + 系统层启动图去掉图标（消除「地球」重影）。versionName 仍为 1.0.3，不跨端改动。
        // versionCode 21 = v1.0.4：头像壁纸清晰度提升 + 物品收藏可编辑 + 隐藏已完成开关等六项迭代，
        // + 深色强调色提亮与按钮前景对比度修复（三端设计规范 v1.0 收口）。
        // versionCode 22 = v1.0.5：切换为正式签名（keystore 由 local.properties 注入 EO_STORE_FILE 等四字段），
        // 与 v1.0.5 发布前准备对齐；内部版本号同步升至 1.0.5。
        // versionCode 23 = v1.0.6：笔记系统替换「数据」底部 Tab（memos 加 markdown 列 DB 7→8）、
        // 相册分类走 DataStore 扩展（零 Room 迁移）、液态玻璃自研（淘汰 liquid-glass/cloudy 库）、richeditor-compose rc10 引入。
        // versionCode 24 = v1.0.7：液态玻璃特效全局开关（设置→外观，DataStore 持久化）+ 全局动效 spring 化 + 主页/底栏玻璃质感复刻 + 背包智能封面/双视图 + 笔记 Notion 化工具栏。
        // versionCode 25 = v1.0.8：全局可读性修复（GlassAccessibilityMode 独立遮罩 + GPU 档着色不透明度抬升）+ 底栏贴合屏幕底部（修复悬浮过高）+ 收藏交互重构（单击打开/预览、长按编辑、编辑态可替换文件）。
        // versionCode 26 = v1.0.9：底栏终版复刻（58dp 胶囊 + 新矢量图标 + 弹跳动效）+ 渐隐蒙版 drawBehind 化根治占位 + 设置页单卡分组/彩色图标芯片 + 笔记工具栏完整外显（斜体/下划线/字号/插入图片/插入文件）。
        // versionCode 27 = v1.0.10：液态玻璃强制常开（移除开关）+ 底栏高光描边/投影/四图标点击动效 + 设置页分组排版 + 全局卡片玻璃化/胶囊按钮 + 任务主线支线合并 + 笔记新建分类/编辑工具栏不被键盘遮挡。
        // versionCode 28 = v1.0.11：笔记 WYSIWYG 重写（richeditor 富文本，去 Markdown 源码暴露）+ 取消默认分类 + 底栏图标重绘（完整背包/倾斜钢笔）+ 底部横杠修复 + 壁纸模糊度滑块 + 设置首页重组/危险区 + 全局 Header 透明与搜索栏胶囊化。
        // versionCode 31 = v1.0.14：顶部状态栏硬遮挡（基础背景色实心填充/不透壁纸）+ 主页避让状态栏 + 笔记撤销重做点击必生效 + 加粗等样式焦点回editor/激活态高亮 + 工具栏紧贴键盘（safeDrawing 消双重 inset）。
        // versionCode 33 = v1.0.16：全局图标现代化升级（笔记工具栏与各页操作图标统一改用圆角风格 Icons.Rounded.*，设置页与底部导航自绘图标不变）。
        // versionCode 34 = v1.0.17：主页 Logo/速览入口/最近动态/人生时间轴/任务页/背包页 Emoji 全面换装圆角矢量 + 笔记工具栏键盘贴合根因修复（imePadding 挂错层级）+ 玻璃卡片可读性强化（scrim 0.28/着色下限 0.66）。
        // versionCode 35 = v1.0.18：底层 UI 组件重构——全局 30 处默认 Card 全部替换为统一 GlassCard（消灭黑色 elevation 阴影）；全站 Emoji 清零换圆角矢量；主页顶部实心块全幅出血；新增「卡片背景不透明度」滑块（DataStore）；笔记工具栏固定标题栏正下方。
        versionCode = 37
        versionName = "1.0.20"
        vectorDrawables { useSupportLibrary = true }
        // 高德 SDK 需要原生 so 库，按需裁剪 ABI（阶段3 接入地图前可放行全部）
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }
    }

    // 签名配置：从 local.properties 读取 EO_STORE_FILE 等字段；未配置时自动跳过，
    // 回退为 Android Studio 默认调试签名，不影响 assembleRelease 编译。
    // 注意：project.findProperty 只读 gradle.properties，不会读 local.properties，
    // 所以这里显式加载 local.properties（与 Android 模板处理 sdk.dir 的方式一致）。
    signingConfigs {
        val localProps = Properties().apply {
            val localFile = rootProject.file("local.properties")
            if (localFile.exists()) localFile.inputStream().use { load(it) }
        }
        val storeFilePath = localProps.getProperty("EO_STORE_FILE")
        val storePwd = localProps.getProperty("EO_STORE_PASSWORD")
        val keyAliasStr = localProps.getProperty("EO_KEY_ALIAS")
        val keyPwd = localProps.getProperty("EO_KEY_PASSWORD")
        if (!listOf(storeFilePath, storePwd, keyAliasStr, keyPwd).any { it.isNullOrBlank() }) {
            create("release") {
                storeFile = rootProject.file(storeFilePath!!)
                storePassword = storePwd!!
                keyAlias = keyAliasStr!!
                keyPassword = keyPwd!!
            }
        }
    }

    buildTypes {
        /* 流畅度关键：debug 包的 android:debuggable=true 会让 ART 不做 AOT 编译（几乎全程解释/JIT 执行），
           同一套 Compose 代码在 debug 与 release 上的帧率差距可以到 2~3 倍。
           所以「体感卡顿」首先要排除 debug 包因素，测流畅度请用 staging / release。 */
        release {
            // 回归结论：R8 minify 在 AGP 8.5.2 / R8 8.5.x 下仍触发 ConcurrentModificationException
            // （minifyReleaseWithR8 FAILED，shaking 期 CME，-dontoptimize 无法规避）。
            // ef4d505 尝试重开 minify 但并未真正修复，且 proguardFiles 当时未挂上。
            // 回退为关闭混淆：与 v1.0.3 已发布版本一致的稳定配置，APK 体积略大但不影响功能与发布。
            // 若日后要重开 minify，需先升级 AGP（至少 8.7.x）或定位 shaking 期 CME 根因，而非仅靠 -dontoptimize。
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            // 保留 proguard 引用（minify 关闭时忽略），便于未来重开时直接生效。
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 未配置正式签名时回退调试签名：保证 assembleRelease 产物可直接装机，
            // 且 SHA1 与已在高德登记的调试版一致（地图 Key 不会失效）
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }

        /* 体验包：不可调试 + 不混淆。拿到「非 debuggable」的 ART 编译收益，
           又完全规避 R8 误删导致的运行时崩溃，用作 release 出问题时的回退选项。 */
        create("staging") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    // View 版 Material 库：提供 Android 资源主题 Theme.Material3.DayNight.NoActionBar（themes.xml 主主题父级）
    implementation(libs.material)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.datastore)
    implementation(libs.coil)

    // 到期提醒：WorkManager（后台检查）+ Hilt Worker 注入
    implementation(libs.workmanager)
    implementation(libs.hiltWork)

    // 网络（AI 对话 + WebDAV 同步，阶段2 接入）
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation("com.google.android.material:material:1.11.0")
    // v1.0.0：读取图片 EXIF 方向（相册里的照片普遍带旋转标记，不转正头像/壁纸会躺倒）
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // 高德地图（阶段3 接入；AndroidManifest.xml 预留 key 占位，拿到 Key 后替换）
    implementation(libs.amap)
    // v1.0.6：笔记系统 Markdown 富文本编辑器（锁定 rc10 兼容 Compose 1.7.x）
    implementation(libs.richeditor)
}
