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
        // versionCode 38 = v1.0.6（最终封板）：内部迭代 1.0.18~1.0.21 统一收敛为对外 v1.0.6 发版，
        // 版本号以此为准（AppInfo.kt / Web core.js / Windows csproj 三端同步），versionCode 只增不减。
        // 本版要点：液态玻璃底层重构 + 全局透明度自定义 + 笔记系统重构 + AI 快捷中枢（真实大模型）
        // + 图标全面圆角矢量化 + 键盘唤起/让位 Bug 修复。
        // versionCode 39 = v1.0.6（补丁批）：AI 配置 UI 重做 + 说明页重写 + 首启资料页进场动效
        // + 出生日期选择器性能优化 + 本地推理闪退根治（架构/内存体检前置 + 自动降级云端）。
        // versionCode 40 = v1.0.6（补丁批二）：笔记伪粗体(Shadow合成) + 日期默认今天 + 清除数据彻底化
        // + 加载失败明确原因 + 内存格式 2.1 GB + 架构/模拟器双门控加固。
        // versionCode 41 = v1.0.6（收尾批）：开启 R8 混淆(minify) + 推理/富文本库保留规则 + 内存不足(<2GB)阻断提示 + 伪粗体加厚。
        // versionCode 44 = v1.0.6（AI 深度修复批）：对话滑动窗口+专属单线程+onCleared中断+流式try-catch；导入返回具体原因(含MNN不支持)；快捷中枢 isAiReady 合并本地引擎 ready 流。
        // versionCode 45 = v1.0.6（AI 彻底融合批）：三点跳动打字指示器（首字即隐）；模型只留 1.5B；模型状态持久化+冷启动静默自动加载；个人资料注入 system prompt（AI 认识用户，能算年龄）；引擎生成 Mutex 排队防并发崩溃；快捷中枢对话同步进全局 ChatHistory；快捷中枢半透明遮罩+用户气泡玻璃加深。
        // versionCode 46 = v1.0.6（Redmi Note 12 Pro / 8GB 防闪退专项）：n_ctx 2048→1024（红线 ≤2048）；历史截断只留最近 2 轮；线程恒 4；生成前内存预检+每秒内存看门狗（<500MB 立即中断生成并 Toast「设备内存不足…」）；年龄改代码层计算注入事实（绝不让模型算）+生日=今天占位兜底。
        // versionCode 47 = v1.0.6（应用内助理收口批）：快捷中枢引擎级防闪退（release/load 与生成全互斥、推理实例锁内捕获、调度器 Dispatchers.IO.limitedParallelism(1)、最新问题截断保留不丢弃）；强约束 Prompt（仅应用内问答+拒绝无关问题+未完成任务/最近笔记摘要注入）；云端配置「测试连接」按钮（/models 优先+1token 回退+精确错误码+耗时）。
        // versionCode 48 = v1.0.6（模拟器实证+全局防卡顿批）：x86_64 AVD 实测取证（门控日志 load blocked / 零 FATAL / 进程存活）；不支持架构禁用「本地模型」选项并提示；全局毛玻璃防卡顿（总内存<4GB 强制 FLAT 纯色档+壁纸全屏高斯仅 FULL 档挂载）；主线程 I/O 收口（scan/hasGguf/内存快照/目录解析与体检全部移入 IO）；打字指示器显隐 derivedStateOf 化。
        // versionCode 49 = v1.0.6（全维沉浸巡检批）：首启出生日期字段整体可点（原 readOnly 字段点击弹无效键盘，真实用户困惑点，修复为全字段点击直接弹日期选择器）。
        // versionCode 50 = v1.0.6（巡检 P1 修复批）：DataStore 加 ReplaceFileCorruptionHandler —— 偏好文件损坏（低存储写入中断等）从「每次启动必崩的无限循环」修复为「回退空偏好自动重建」，最多丢偏好、绝不闪退。
        // versionCode 51 = v1.0.6（Tool Calling 终版批）：AI 对话支持 OpenAI 兼容 tools —— 模型可调用本地工具 create_task / complete_task / create_note（Room 落库 + 动态/XP 流水同口径），结果回传生成最终回复；服务端不支持 tools 时自动剥掉重试，绝不崩溃。首启引导右上角新增「跳过」。
        versionCode = 51
        versionName = "1.0.6"
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
            // v1.0.6 收尾：开启 R8 混淆（瘦身 + 提高逆向门槛）。
            // 仍保留 -dontoptimize：规避 AGP 8.5.x / R8 8.5.35 在类合并（class merging，属优化期）的
            // ConcurrentModificationException；关闭优化后只做 shrink + obfuscate，瘦身与混淆照常生效，
            // 仅失去内联/合并，对功能与体积影响可忽略。保留规则见 proguard-rules.pro。
            isMinifyEnabled = true
            isShrinkResources = false
            isDebuggable = false
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
    // v1.0.6.5：本地离线推理引擎（llama.cpp 绑定，GGUF；预编译 so 随包分发，无需 NDK 自编译）
    implementation(libs.inferkt)
}
