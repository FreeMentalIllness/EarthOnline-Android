package com.example.earthonline.ui.ai

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.data.network.AiConfig
import com.example.earthonline.data.network.AiRepository
import com.example.earthonline.data.network.ChatMessage
import com.example.earthonline.data.network.LocalModelConfig
import com.example.earthonline.data.network.ToolCallRequest
import com.example.earthonline.data.repository.AiChatHistoryRepository
import com.example.earthonline.data.repository.ProfileRepository
import com.example.earthonline.ui.ai.LocalModelManager
import com.example.earthonline.util.todayStr
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class UiMessage(val role: String, val content: String)

@HiltViewModel
class AiViewModel @Inject constructor(
    private val repo: AiRepository,
    private val settings: SettingsDataStore,
    private val json: Json,
    /** v1.0.6（融合重构 · 死命令 #4）：个人资料 —— 昵称 / 生日 / 自定义字段注入 system prompt */
    private val profileRepo: ProfileRepository,
    /** v1.0.6（融合重构 · 死命令 #5）：全局对话历史 —— 快捷中枢与对话页共享同一份 */
    private val chatHistory: AiChatHistoryRepository,
    /** v1.0.6.13（死命令 #2）：应用内数据上下文 —— 未完成任务与最近笔记注入 system prompt */
    private val taskRepo: com.example.earthonline.data.repository.TaskRepository,
    private val memoRepo: com.example.earthonline.data.repository.MemoRepository,
    /** v1.0.6（Tool Calling 批）：AI 「能改」的本地执行器（建任务 / 完任务 / 记笔记） */
    private val toolExec: com.example.earthonline.ui.ai.AiToolExecutor,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _messages = MutableStateFlow<List<UiMessage>>(
        listOf(UiMessage("assistant", "你好，我是地球Online的「系统」。告诉我你今天想完成什么？"))
    )
    val messages = _messages.asStateFlow()

    init {
        // 死命令 #5 数据同步：进入对话页先恢复全局历史（含快捷中枢里说过的对话）
        viewModelScope.launch {
            val history = runCatching { chatHistory.load() }.getOrDefault(emptyList())
            if (history.isNotEmpty()) {
                _messages.value = _messages.value + history.map { UiMessage(it.role, it.content) }
            }
        }
    }

    private val _input = MutableStateFlow("")
    val input = _input.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    val aiConfigFlow = settings.aiConfig
    /** 本地模型（离线推理）配置 JSON */
    val localModelJson: Flow<String> = settings.localModel

    fun onInputChange(s: String) { _input.value = s }

    fun parseAiConfig(s: String): AiConfig =
        runCatching { json.decodeFromString<AiConfig>(s) }.getOrDefault(AiConfig())

    fun saveAiConfig(c: AiConfig) = viewModelScope.launch {
        settings.setAiConfig(json.encodeToString(AiConfig.serializer(), c))
    }

    fun parseLocalModel(s: String): LocalModelConfig =
        runCatching { json.decodeFromString<LocalModelConfig>(s) }.getOrDefault(LocalModelConfig())

    fun saveLocalModel(c: LocalModelConfig) = viewModelScope.launch {
        settings.setLocalModel(json.encodeToString(LocalModelConfig.serializer(), c))
    }

    fun clearError() { _error.value = null }

    /**
     * v1.0.6（融合重构 · 死命令 #3）：本地模型「已加载」标志持久化。
     * 加载成功写 true → 下次冷启动静默自动加载；卸载写 false。
     */
    fun setLocalModelLoaded(v: Boolean) = viewModelScope.launch {
        runCatching { settings.setLocalModelLoaded(v) }
    }

    /** 清空对话：UI 重置 + 全局历史一并清空（快捷中枢与对话页保持同步） */
    fun clearChat() {
        _messages.value = listOf(
            UiMessage("assistant", "你好，我是地球Online的「系统」。告诉我你今天想完成什么？")
        )
        viewModelScope.launch { runCatching { chatHistory.clear() } }
    }

    /**
     * 死命令 #2：内存不足中断生成时的用户提示（Toast）。
     * viewModelScope 默认主线程派发，Toast 可直接弹；runCatching 兜底任何 UI 异常。
     */
    private fun toastMemoryAbort() {
        runCatching {
            android.widget.Toast.makeText(
                appContext, AI_MEMORY_ABORT_MSG, android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * v1.0.6.13（死命令 #2）：强约束 System Prompt —— AI 使用场景已明确为
     * **仅查询/操作「地球Online」应用内数据，不做深度通用对话**。
     *
     * 注入四块内容（都做尺寸收敛，1024 窗口下 system 总量 ≈ 400~450 token）：
     *  ① 严格角色限制：无关问题一律拒绝（死命令原文）；
     *  ② 今天日期；
     *  ③ 个人资料事实（年龄在代码层算准 —— 「用户当前真实年龄为 X 岁」格式注入，
     *    绝不让 1.5B 模型自己算；生日为空/非法/未来/等于今天一律不注入）；
     *  ④ 应用内数据摘要：未完成任务 ≤8 条 + 最近笔记 ≤5 条（都过滤软删、限长），
     *    AI 因此能回答「我今天有什么事」「我上周记了什么」这类应用内查询。
     */
    private suspend fun buildProfileSystemPrompt(): String {
        val role = "你只是「地球Online」应用内的个人助理，仅负责查询和操作本应用内的数据" +
            "（个人资料、任务、笔记、背包、足迹）。严禁回答与用户个人数据、任务、笔记无关的" +
            "通用问题（新闻、写代码、百科、闲聊等）；用户询问无关内容时，直接礼貌拒绝并引导回应用内事务。回答简洁、准确、友好。"
        val today = "今天的日期是 ${todayStr()}。"
        return runCatching {
            val p = profileRepo.get()
            val nickname = p?.name?.takeIf { it.isNotBlank() }
            val birth = p?.birthDate?.takeIf { it.isNotBlank() }
            // 死命令：年龄在代码层算准（Calendar 实现 = Period.between().years；非法/未来日期返回 null）
            val age = birth?.let { com.example.earthonline.util.ageFromBirthDate(it) }
            // 死命令兜底：生日等于系统今天 = 资料页的占位默认值，绝不能当真实生日用
            val birthValid = birth != null && age != null && birth != todayStr()
            val gender = when (p?.gender) {
                "male" -> "男"
                "female" -> "女"
                else -> ""
            }
            val region = listOfNotNull(
                p?.country?.takeIf { it.isNotBlank() },
                p?.province?.takeIf { it.isNotBlank() }
            ).joinToString("·")
            val custom = runCatching {
                json.decodeFromString<List<com.example.earthonline.data.model.CustomField>>(
                    p?.customFieldsJson ?: ""
                )
            }.getOrDefault(emptyList())
                .filter { it.label.isNotBlank() && it.value.isNotBlank() }
                .take(5)
                .joinToString("；") { "${it.label}：${it.value.take(30)}" }

            // ④ 应用内数据摘要（软删行过滤：deletedAt 非空 = 回收站内容，不进 prompt）
            val tasks = runCatching {
                taskRepo.observeAll().first()
                    .filter { it.deletedAt == null && it.status != "done" && it.title.isNotBlank() }
                    .take(8)
            }.getOrDefault(emptyList())
            val taskLine = if (tasks.isEmpty()) ""
            else "用户当前未完成任务：" + tasks.joinToString("；") { it.title.take(16) } + "。"

            val memos = runCatching {
                memoRepo.observeAll().first()
                    .filter { it.deletedAt == null && it.text.isNotBlank() }
                    .sortedByDescending { it.createdAt }
                    .take(5)
            }.getOrDefault(emptyList())
            val memoLine = if (memos.isEmpty()) ""
            else "用户最近的笔记：" + memos.joinToString("；") { it.text.take(24) } + "。"

            val facts = buildList {
                nickname?.let { add("用户昵称是「$it」") }
                if (gender.isNotBlank()) add("性别$gender")
                if (birthValid) {
                    add("出生于$birth")
                    add("用户当前真实年龄为 $age 岁（这是代码层精确计算的事实，被问年龄时直接引用，不要自行推算）")
                }
                if (region.isNotBlank()) add("所在地：$region")
                if (custom.isNotBlank()) add("补充资料：$custom")
            }
            val factLine = if (facts.isEmpty()) "用户尚未填写个人资料。" else facts.joinToString("，") + "。"
            "$role $today $factLine $taskLine $memoLine".trim()
        }.getOrDefault("$role $today")
    }

    /** 页面退出时中断正在进行的本地推理，避免协程越界写已销毁的 UI State */
    fun cancelGeneration() {
        runCatching { LocalLlmRuntime.engine.cancel() }
    }

    /**
     * ViewModel 销毁时（退出对话页 / 进程低内存回收）强制中断后台推理协程，
     * 释放当前生成的 native KV 缓冲（stopGeneration），杜绝「协程还在跑、
     * 页面已销毁」的越界写闪退。模型**保持加载**（单例），不在此 unload，
     * 以便主页快捷中枢继续复用，避免反复重载。
     */
    override fun onCleared() {
        cancelGeneration()
    }

    /**
     * 发送（v1.0.6.5 三态开关）：
     *  - off：直接提示已关闭，不发任何请求；
     *  - local：走本地引擎（当前占位 → 诚实提示；真实 MNN 引擎接入后自动流式）；
     *  - cloud（默认）：OpenAI 兼容 SSE 流式，逐 token 打字机。
     */
    fun send() {
        val text = _input.value.trim()
        if (text.isBlank() || _loading.value) return
        _input.value = ""
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            val rawJson = settings.aiConfig.first()
            var mode = runCatching { json.decodeFromString<AiConfig>(rawJson) }.getOrNull()
                ?.runMode?.ifBlank { "cloud" } ?: "cloud"

            if (mode == "off") {
                _loading.value = false
                _error.value = "AI 已关闭，请到「设置 → 通用 → AI 对话」开启"
                return@launch
            }

            _messages.value = _messages.value + UiMessage("user", text)
            // 流式：先占位一条空 assistant 消息，再逐 token 增量拼接（打字机效果）
            _messages.value = _messages.value + UiMessage("assistant", "")
            val idx = _messages.value.lastIndex
            val sb = StringBuilder()
            fun commit(s: String) {
                // 防御：并发流式 / 用户清空对话都可能让 idx 失效，
                // 直接下标赋值会抛 IndexOutOfBounds 把整个页面掀掉
                _messages.value = _messages.value.toMutableList().apply {
                    if (idx in indices) this[idx] = UiMessage("assistant", s)
                    else add(UiMessage("assistant", s))
                }
            }

            /* 本地模式「体检」：任何一项不过关都在触碰 JNI 之前降级到云端。
               native 层的 OOM / SIGABRT 是 try/catch 拦不住的进程级崩溃，
               唯一可靠的做法就是不让它发生。
               v1.0.6.14（死命令 #3 主线程守护）：目录解析（含 mkdirs 磁盘 I/O）与
               diagnose（ActivityManager 系统调用）整体移入 IO 协程。 */
            val degradeNotice: String? = if (mode == "local") withContext(Dispatchers.IO) {
                val localCfg = parseLocalModel(settings.localModel.first())
                val dir = java.io.File(
                    localCfg.modelDir.ifBlank {
                        LocalModelManager.modelDir(appContext, ModelDownloader.MODEL_NAME).absolutePath
                    }
                )
                when (val diag = LocalLlmRuntime.diagnose(dir)) {
                    is LocalLlmSupport.Blocked -> diag.short
                    LocalLlmSupport.Ready -> if (LocalLlmRuntime.engine.ready) null
                    else "本地模型尚未加载：请先到「设置 → 通用 → AI 对话」导入 GGUF 并点击「加载模型」。当前已改用云端模式。"
                }
            } else null
            if (degradeNotice != null) mode = "cloud"

            // 死命令 #4：全局上下文注入 —— 个人资料拼进 system prompt，本地 / 云端两路共用
            val profilePrompt = buildProfileSystemPrompt()

            try {
                if (mode == "local") {
                val engine = LocalLlmRuntime.engine
                val history = _messages.value.take(idx).map { ChatMessage(it.role, it.content) }
                // 注入带个人资料的 system 指令（AI 认识用户）：Qwen 在无 system 时极易输出空/重复
                // （「变哑巴」诱因之一），必须手动拼入 ChatML 模板（见 LocalLlmEngine.buildQwenPrompt）。
                val withSystem = if (history.none { it.role == "system" })
                    listOf(ChatMessage("system", profilePrompt)) + history
                else history
                val reply = engine.chat(withSystem) { delta ->
                    sb.append(delta)
                    commit(sb.toString())
                }
                // 终态：reply 始终非空（正常回复 / 真实报错 / 超时部分内容），直接 commit 最终内容；
                // 仅在既无 reply 又无任何流式输出时才给兜底文案。
                if (reply != null) {
                    commit(reply)
                    // 死命令 #2：内存看门狗中断 → UI 弹 Toast（文案与引擎常量一致）
                    if (reply == AI_MEMORY_ABORT_MSG) toastMemoryAbort()
                }
                else if (sb.isEmpty()) commit("本地推理无返回：可在「设置 → 通用 → AI 对话」卸载后重试，或改用云端模式")
            } else {
                    val cfg = parseAiConfig(rawJson)
                    val history = _messages.value.take(idx).map { ChatMessage(it.role, it.content) }
                    when {
                        degradeNotice != null && (cfg.baseUrl.isBlank() || cfg.apiKey.isBlank() || cfg.model.isBlank()) ->
                            // 本地不可用 + 云端未配置：给一条**明确标注的模拟回复**，
                            // 绝不留空白让用户以为又卡死了（也绝不伪装成真模型输出）
                            commit(
                                com.example.earthonline.ui.ai.MockLlmEngine.mockReply(text, degradeNotice)
                            )
                        cfg.baseUrl.isBlank() || cfg.apiKey.isBlank() ->
                            commit("请先在「设置 → AI 对话」中配置 Base URL 与 API Key")
                        cfg.model.isBlank() ->
                            commit("请先在「设置 → AI 对话」中填写模型名（按服务商文档照抄）")
                        else -> {
                            if (degradeNotice != null) commit(degradeNotice)
                            // 降级提示与真实回答分两条消息：提示不参与上下文，避免污染模型输入
                            val clean = cfg
                            // 死命令 #4：云端路径同样注入个人资料 system prompt
                            val convo: List<ChatMessage> = listOf(ChatMessage("system", profilePrompt)) + history
                            val prefix = if (degradeNotice != null) degradeNotice + "\n\n" else ""

                            /* v1.0.6（Tool Calling 批）：AI 不止「能看」还能「能改」。
                               首轮仍走流式（打字机体验不变），同时带上本地工具定义；
                               模型若发起 tool_calls → 本地执行 Room 操作 → 结果回传 → 非流式快轮要最终回答。
                               任何一环失败都折叠成友好文案，绝不把协议异常抛给用户。 */
                            val calls = mutableListOf<ToolCallRequest>()
                            try {
                                repo.streamChat(clean, convo, tools = AiToolSpecs.all) { got ->
                                    calls.clear(); calls.addAll(got)
                                }.collect { delta ->
                                    sb.append(delta)
                                    commit(
                                        (if (degradeNotice != null) degradeNotice + "\n\n" else "") + sb.toString()
                                    )
                                }
                            } catch (ce: kotlinx.coroutines.CancellationException) {
                                throw ce
                            } catch (first: Throwable) {
                                if (calls.isEmpty()) {
                                    // 大概率是服务端不支持 tools 参数：剥掉 tools 原样重试一次（纯对话能力不受影响）
                                    try {
                                        sb.clear()
                                        commit(prefix)
                                        repo.streamChat(clean, convo).collect { delta ->
                                            sb.append(delta)
                                            commit(prefix + sb.toString())
                                        }
                                    } catch (ce2: kotlinx.coroutines.CancellationException) {
                                        throw ce2
                                    } catch (t2: Throwable) {
                                        // 第二次失败交给外层统一友好提示
                                        throw t2
                                    }
                                } else throw first
                            }
                            if (calls.isNotEmpty()) {
                                runCloudToolLoop(clean, convo, calls.toList(), sb.toString(), prefix) { commit(it) }
                            } else if (sb.isEmpty()) {
                                commit(prefix + "（无返回内容）")
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                // 注意抓 Throwable 而不是 Exception：UnsatisfiedLinkError / OOM 都是 Error，
                // 历史上正是它们穿透了 Exception 分支，直接把进程掀掉
                android.util.Log.e("EarthOnline-AI", "chat failed: ${t.javaClass.simpleName}", t)
                val friendly = when (t) {
                    is java.net.UnknownHostException, is java.net.ConnectException ->
                        "无网络连接，请检查网络后重试"
                    is java.net.SocketTimeoutException ->
                        "请求超时，请稍后重试"
                    is java.io.IOException ->
                        "网络异常，请检查网络后重试"
                    is OutOfMemoryError ->
                        "内存不足，已中止本次请求。可到「设置 → AI 对话」点「卸载释放」后重试"
                    is UnsatisfiedLinkError, is NoClassDefFoundError ->
                        "本地推理原生库不可用，已切模拟模式。请改用云端模型"
                    else -> "服务异常：${t.message ?: t.javaClass.simpleName}"
                }
                commit(friendly)
            } finally {
                _loading.value = false
                // 死命令 #5 数据同步：终态一次性写入全局历史（流式过程不写，避免 DataStore 高频写）。
                // 写入的是用户这句话 + 气泡最终呈现的完整内容（含真实报错 / 兜底文案，脉络不丢）。
                val finalText = _messages.value.getOrNull(idx)?.content.orEmpty()
                launch { runCatching { chatHistory.append(text, finalText) } }
            }
        }
    }

    /**
     * v1.0.6（Tool Calling 批）：工具循环 —— AI 说要改数据，本地真正动手，结果回传再要最终回答。
     *
     * 时序：执行全部待调工具（顺序与模型请求一致）→ 摘要实时上屏 →
     * 按 OpenAI 协议把 assistant(tool_calls) + role=tool 结果追加进上下文 →
     * 非流式快轮请求最终回答；模型若继续发起工具就再来一轮。
     *
     * 防失控：最多 3 轮，超限主动止损并明说；执行摘要跨轮累计，最终回答一并呈现，
     * 用户始终能看到「AI 到底替我动了哪些数据」。
     */
    private suspend fun runCloudToolLoop(
        cfg: com.example.earthonline.data.network.AiConfig,
        convo0: List<ChatMessage>,
        calls0: List<ToolCallRequest>,
        firstLeadIn: String,
        prefix: String,
        commit: (String) -> Unit
    ) {
        var convo = convo0
        var calls = calls0
        val lead = firstLeadIn.trim()
        val done = mutableListOf<String>()
        var rounds = 0
        while (calls.isNotEmpty() && rounds < 3) {
            rounds++
            // ① 本地执行（执行器内部兜底一切异常，永不抛出）
            val results = mutableListOf<Pair<ToolCallRequest, String>>()
            for (c in calls) {
                val res = toolExec.execute(c)
                results += c to res
                done += (if (toolExec.isOk(res)) "✅ " else "⚠️ ") + toolExec.briefOf(c)
                commit(headline(prefix, lead, done) + "\n正在生成回复…")
            }
            // ② 协议固定套路：模型要调的工具 + 本地执行结果，追加进上下文
            convo = convo +
                ChatMessage(role = "assistant", toolCalls = results.map { it.first }) +
                results.map { (c, r) ->
                    ChatMessage(role = "tool", content = r, toolCallId = c.id, name = c.function.name)
                }
            // ③ 非流式快轮要最终回答
            val resp = try {
                repo.chat(cfg, convo, tools = AiToolSpecs.all)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                commit(
                    headline(prefix, lead, done) +
                        "\n\n⚠️ 工具已执行成功，但生成最终回复失败：${(t.message ?: t.javaClass.simpleName).take(120)}"
                )
                return
            }
            resp.error?.message?.takeIf { it.isNotBlank() }?.let { err ->
                commit(headline(prefix, lead, done) + "\n\n⚠️ 上游服务返回错误：${err.take(120)}")
                return
            }
            val msg = resp.choices.firstOrNull()?.message
            val next = msg?.toolCalls.orEmpty()
            if (next.isNotEmpty()) {
                calls = next
            } else {
                val body = msg?.content.orEmpty().trim()
                commit(
                    if (body.isBlank()) headline(prefix, lead, done) + "\n（工具已执行，但模型没有给出后续回复）"
                    else headline(prefix, lead, done) + "\n\n" + body
                )
                return
            }
        }
        // 连续 3 轮仍在要工具：主动止损，防止失控刷库
        commit(
            headline(prefix, lead, done) +
                "\n\n⚠️ 已连续执行 3 轮工具调用，为防误操作先停在这里。可以把需求拆小一点再说一次。"
        )
    }

    private fun headline(prefix: String, lead: String, done: List<String>): String =
        prefix + (if (lead.isBlank()) "" else lead + "\n\n") + done.joinToString("\n")
}
