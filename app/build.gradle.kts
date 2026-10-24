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
        // versionCode 52 = v1.0.6（MNN 引擎移植批）：本地离线推理新增 MNN 引擎（移植自开源
        // messageAIHelper 的 libMNN.so + liblocalllm.so 预编译栈，仅 arm64-v8a），Qwen2.5-1.5B
        // 在真机可直接跑；模型导入接受 MNN 格式（llm.mnn+llm.mnn.weight）并逐条目显示解压进度；
        // n_ctx 锁 1024、生成封顶 512 token 防 8GB 机型 OOM；非 arm64 / 模拟器自动降级云端。
        // versionCode 53 = v1.0.6（死磕细节·动效体验批）：全局按压回弹 BouncyClickable（卡片/列表项/入口按下缩 0.98 极低阻尼弹回）；
        // 全站弹窗由居中 AlertDialog 统一改为 ModalBottomSheet 底部弹簧上浮（skipPartiallyExpanded 干脆到位）；
        // 导航转场分两支——Tab 间淡入+微垂直位移、二级页右侧推入/平滑推出；列表滚动中玻璃模糊全局降级为纯半透明底色（保 60FPS），
        // 停止恢复；FAB 接入 bouncyPress 视觉回弹。
        // versionCode 54 = v1.0.6（终极重构批）：① 全局 Design Tokens + EO 组件族
        // （EOGlassCard / EOInput / EOBottomSheet / EOConfirmDialog / EOEmptyState），破坏性操作强制二次确认；
        // ② 业务连贯：任务备注复用笔记富文本（Markdown 存储，零迁移）、彻底清空含 DataStore/壁纸/模型、
        // 诚实空状态与 AI 未配置引导；③ 游戏化：GamificationEngine 统一 XP→等级换算、**废弃连击机制**、
        // 等级进度条平滑增长 + 飘出 +XP 微动效、背包收藏页新增 Steam 游戏数据模块；
        // ④ AI 闭环：n_ctx 收敛 512 防 OOM、800MB 解压改前台服务（通知栏进度 + 进程保活）。
        // versionCode 55 = v1.0.6（真机反馈修复批）：外观默认值收敛(壁纸100/模糊0/卡片100)+
        // 修复外观设置保存不生效；全站二级窗口由底部抽屉改为居中固定 GlassDialog；
        // AI 页只留一个导入入口 + 模型加载失败红字明示(架构/内存/文件损坏)+MNN 加载链路修复；
        // 撤销列表滚动模糊降级(液态玻璃常驻)；取消默认 Emoji 头像改矢量占位；
        // 主页新增轻量日历卡；任务备注工具栏去 B/I/U；Steam 游戏库独立二级页；笔记待办打勾不吞字
        // versionCode 56 = v1.0.6（四处闪退根治 + 四项体验修复批）：[真机 Logcat 实证]
        // ① 修复四处闪退(任务页加号/隐私政策/更新日志/AI配置) —— 根因为 GlassDialog 外层
        //    verticalScroll 与各弹窗 text 槽内层 verticalScroll 同向嵌套，被 Compose 以 infinity
        //    约束抛 IllegalStateException；统一去掉弹窗内层滚动，改由 GlassDialog 单层滚动承载。
        // ② 彻底清除默认 Emoji 头像预设（删除 AvatarPresets emoji 数组），更换头像弹窗仅保留
        //    「从相册上传图片」与「重置为默认（极简 User 轮廓矢量图标）」两项。
        // ③ 卡片动态毛玻璃：废除「100% 不透明跳过壁纸采样」逻辑，GlassSurface 常驻按位置实时采样壁纸，
        //    Android 12+ 用 RenderEffect.createBlurEffect 真高斯模糊，随滑动产生折射光影变化。
        // ④ 笔记待办改为标准 Compose Checkbox 清单（勾选加删除线），废弃「- [ ] 」文字符号替换。
        // ⑤ 主页日历卡支持点击任意日期：选中高亮 + 下方任务列表同步刷新当天数据。
        // versionCode 57 = v1.0.6（主页布局优化 + 渲染修复批）：
        // ① 修复卡片灰/白相间色块断层：GlassSurface 精简为「单层 RenderEffect 模糊壁纸 +
        //    单层平滑半透明遮罩」，废弃多层叠加 / 整屏 FillBounds / 每卡位置偏移；模糊半径提到 14dp。
        // ② 主页速览入口拆分：「任务」→「日常」+「Todo」，「背包」→「物品」+「收藏」（旧键自动迁移）。
        // ③ 主页卡片堆叠 Stack：时间轴 / 人生卡片收入堆叠区，HorizontalPager 左右滑动切换 +
        //    页码指示 + 自定义入堆叠卡片（DataStore 持久化）。
        // ④ 笔记行内复选框：取消独立待办清单，改为行式编辑器 + 行首真实 Compose Checkbox，
        //    勾选加删除线；回车拆行、行首退格合并；Markdown 待办行存取跨端兼容。
        // ⑤ 任务时间选择器增加钟表盘（Material3 TimePicker analog dial），时间追加进到期日 HH:mm。
        // ⑥ 父子任务层级缩进优化：INDENT 18→26dp + 引导线加深 + 祖先列淡色「车道」底色。
        // versionCode 58 = v1.0.6（深度测试反馈修复批）：
        // ① 全局去壁纸化：GlassSurface 遮罩 Alpha 0.34~0.66 → 0.72~0.88（卡片不再直接透出
        //    复杂壁纸，文字任何壁纸下清晰）；全部二级页 Scaffold 加 0.96 高透明度表面背板
        //    （地图页除外，承载高德视图须透明）。
        // ② 全局对比度修复：二级/三级文字加深（#7A7268→#6A6258 / #B0A89C→#8D8478）；
        //    日历非高亮日期文字加深、选中日期暖棕实心 + 3dp 主题色描边；任务页 TabRow
        //    加深容器 + 未选中 Tab 1.5dp 主题色描边胶囊。
        // ③ 交互解耦：卡片堆叠移至首页 LazyColumn 最顶部；今日一签 / 最近动态 / 日程整合进
        //    堆叠卡片（HorizontalPager 纯展示，剥离编辑按钮，点击整卡跳详情页）；
        //    速览「记账」→ 快捷记账键盘输入面板（金额数字键盘自动弹出），「Todo」→ 新建任务弹窗。
        // ④ 笔记页回退：NoteEditorScreen 恢复至 0a3c86f（v1.0.6.6 标准 Checkbox 清单稳定版），
        //    移除行式编辑器引入的打勾 Bug。
        // ⑤ 排版修复：自定义最近动态弹窗「记录」标签竖排 / 「全部」等标签挤压 → FlowRow 换行；
        //    「+ 添加自」截断 → 补全「+ 添加自定义」并加大按钮容器宽度。
        // ⑥ 悬浮「+」滚动监听：LazyColumn 向下滑动且 isScrollInProgress 时 AnimatedVisibility
        //    隐藏，向上滑 / 停止恢复，不再遮挡列表。
        // versionCode 59 = v1.0.6（深度测试反馈二轮修复批）：
        // ① 笔记分类层级：先建分类（文件夹）再在分类下建笔记，取消平铺；分类/归属走
        //    DataStore JSON（NOTE_FOLDERS/NOTE_FOLDER_MAP），零 Room 迁移、不动同步契约；
        //    笔记卡新增「移动到分类」；筛选态临时平铺。
        // ② 任务页 UI 对齐背包：卡片内边距 10/8dp→12dp、列表底部预留 88dp FAB 让位、
        //    Todo 表格行包 AppCard；父子层级 INDENT 26→40dp + 连接线 2.5dp/加深/车道加重。
        // ③ 导航转场统一：AppNavHost 四向全部改弹簧（damping 0.92/MediumLow）Fade+Slide。
        // ④ 地图/足迹搜索解耦：地图顶栏只搜 POI（就地输入出结果，废独立 POI 弹窗）；
        //    足迹搜索移入「路径点记录」面板顶部专用搜索框（本地名称/备注/标签/日期）。
        // ⑤ 记账回滚为应用内账本页：新 AccountingScreen（月度汇总+快捷录入键盘+流水，
        //    DataStore JSON 持久化），删除外部 App 下载引导（AccountingDialog.kt 移除）；
        //    速览记账 = 跳账本页 + 自动弹金额键盘。
        // ⑥ 速览操作化：记账→账本页+键盘、Todo→new_todo 路由（任务页 Todo Tab+新建弹窗）、
        //    日常→任务页、笔记→直达新建编辑页。
        // ⑦ 真正卡片堆叠：位置下移至速览正下方；HorizontalPager pageSize 收窄+contentPadding
        //    居中，当前卡居中、相邻卡边缘露出，左右滑动翻页。
        // ⑧ 彻底去壁纸化（死命令）：GlassSurface 新增 solid 档（0.97 近不透明+不采样壁纸），
        //    GlassDialog/AnimatedAlertDialog 全量强制；二级页 containerColor 改纯色 background；
        //    AppNavHost 按路由收编壁纸作用域（仅主页/任务/背包/笔记四个 Tab 及其变体透出）。
        // versionCode 60 = v1.0.6（深度测试反馈三轮修复批，死命令模式）：
        // ① 记账回归外链跳转（撤销 vc59 ⑤ 应用内账本页）：删除 AccountingScreen/ViewModel，
        //    速览/菜单「记账」改走 launchAccounting —— 已配置记账 App 包名则 Intent 直启，
        //    否则浏览器打开下载页（恢复 v1.0.1 外链行为）；Screen.Accounting 路由移除。
        // ② 任务与笔记页 UI 对齐背包页：统一 AppCard（圆角 16dp / 同套描边 / 无黑色 elevation）
        //    + TopAppBar containerColor 用 background，列表卡内边距 12dp。
        // ③ 任务备注复选框美化：MarkdownRemark 把备注里的「- [ ] / - [x]」渲染为品牌色
        //    标准 Compose Checkbox（圆角琥珀色 + 勾选删除线），可回写。
        // ④ 笔记编辑取消独立待办模块：待办内联为正文普通行（工具栏插入「- [ ] 」），
        //    不再拆出独立清单；读取/保存直接走 RichTextState Markdown。
        // ⑤ 主页 AI 快捷 FAB 卡顿根治：hideFab 改 derivedStateOf 同时观察 isScrollInProgress
        //    与方向位，停滚即回显（修掉「停滚后卡在隐藏态」），方向阈值降到 +4 更跟手。
        // ⑥ 速览默认收起：收起态只固定 6 个核心入口（相册/地图/记账/数据/成就/系统），
        //    日常/Todo/物品/收藏/笔记 5 项默认收进「展开/更多」区，点按钮平滑放出。
        // ⑦ 彻底去壁纸化（死命令根因修复）：GlassSurface 彻底移除壁纸采样（AsyncImage +
        //    RenderEffect 背景层删除），卡片/弹窗底板恒为 surface 高不透明度纯色（0.97~1.0，
        //    默认 1.0 完全不透明），任意花壁纸上文字绝对清晰；清理相关失效 import 与注释。
        versionCode = 60
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
