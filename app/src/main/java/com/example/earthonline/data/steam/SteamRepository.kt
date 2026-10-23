package com.example.earthonline.data.steam

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Steam 游戏库数据（终极重构 · 工程三）。
 *
 * 数据来源：Steam Web API `IPlayerService/GetOwnedGames`。
 * 只做**只读拉取**（拥有游戏列表 + 时长 + 最近游玩），不做任何写操作，
 * 也不需要用户登录授权 —— 用户自备 Steam ID 与 Web API Key。
 *
 * 诚实原则（No fake data, ever）：
 *  - 未配置 / 拉取失败一律抛异常或返回空，由上层显示「未配置 / 拉取失败」引导，
 *    **绝不用占位假数据填充游戏列表**；
 *  - 时长为 0 的游戏也会如实展示（那是真实的「买了没玩」），不做美化过滤。
 */

/** 单款游戏 */
data class SteamGame(
    val appid: Long,
    val name: String,
    /** 总游玩时长（分钟，Steam 原始单位） */
    val playtimeMinutes: Int,
    /** 最近两周时长（分钟） */
    val playtime2Weeks: Int,
    /** 图标 hash，用于拼 Steam 图片 CDN 地址 */
    val iconHash: String,
    /** 最近游玩时间戳（秒，0 = 从未玩过） */
    val lastPlayed: Long
) {
    /** 总时长（小时，保留 1 位） */
    val playtimeHours: Float get() = playtimeMinutes / 60f

    /** Steam 封面图地址（header image；无 hash 时返回空串由 UI 降级为占位） */
    val headerImageUrl: String
        get() = if (appid > 0) {
            "https://cdn.cloudflare.steamstatic.com/steam/apps/$appid/header.jpg"
        } else ""
}

/** 拉取结果：成功带列表，失败带**具体原因**（直接展示给用户，不伪造成功） */
sealed class SteamResult {
    data class Ok(val games: List<SteamGame>, val totalCount: Int) : SteamResult()
    data class Fail(val reason: String) : SteamResult()
}

object SteamRepository {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 拉取拥有游戏列表。**阻塞调用，必须在 Dispatchers.IO 上执行。**
     *
     * @param steamId Steam 64 位 ID
     * @param apiKey Steam Web API Key
     * @return [SteamResult.Ok]（按时长倒序，最多 [TOP_N] 条）或 [SteamResult.Fail]（含可读原因）
     */
    fun fetchOwnedGames(steamId: String, apiKey: String): SteamResult {
        val id = steamId.trim()
        val key = apiKey.trim()
        // 诚实原则：参数不全直接报「未配置」，绝不拿空参数去撞 API 再伪装成 0 款游戏
        if (id.isBlank() || key.isBlank()) {
            return SteamResult.Fail("尚未配置 Steam ID 或 API Key")
        }
        if (!id.all { it.isDigit() }) {
            return SteamResult.Fail("Steam ID 格式有误（应为纯数字的 64 位 ID）")
        }
        return runCatching {
            val url = "https://api.steampowered.com/IPlayerService/GetOwnedGames/v0001/" +
                "?key=$key&steamid=$id&format=json" +
                "&include_appinfo=1&include_played_free_games=1&include_last_played=1"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                when (resp.code) {
                    401, 403 -> return SteamResult.Fail("API Key 无效或无权访问（${resp.code}）")
                    !in 200..299 -> return SteamResult.Fail("Steam 接口返回异常（${resp.code}）")
                }
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return SteamResult.Fail("Steam 返回内容为空")
                parse(body)
            }
        }.getOrElse { e ->
            SteamResult.Fail(
                when {
                    e is java.net.UnknownHostException -> "网络不可用，无法连接 Steam"
                    e is java.net.SocketTimeoutException -> "请求超时，请稍后重试"
                    else -> "拉取失败：${e.message ?: e::class.simpleName}"
                }
            )
        }
    }

    /** 展示条数上限：只展示游玩时长最长的若干款，避免几百科目拖垮列表 */
    const val TOP_N = 12

    private fun parse(body: String): SteamResult {
        return runCatching {
            val root = JSONObject(body)
            // 隐私设置未公开时，Steam 会返回 {"response":{}} —— 这是真实状态，如实告知
            val response = root.optJSONObject("response")
                ?: return SteamResult.Fail("Steam 返回结构异常")
            if (!response.has("games")) {
                return SteamResult.Fail(
                    "拉取不到游戏库：请确认该账号的「游戏详情」隐私设置为公开"
                )
            }
            val arr = response.getJSONArray("games")
            val total = response.optInt("game_count", arr.length())
            val games = mutableListOf<SteamGame>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                games += SteamGame(
                    appid = o.optLong("appid", 0L),
                    name = o.optString("name", "").ifBlank { "未知游戏 #${o.optLong("appid")}" },
                    playtimeMinutes = o.optInt("playtime_forever", 0),
                    playtime2Weeks = o.optInt("playtime_2weeks", 0),
                    iconHash = o.optString("img_icon_url", ""),
                    lastPlayed = o.optLong("rtime_last_played", 0L)
                )
            }
            // 按时长倒序 —— 用户真正投入的游戏排在前面
            val top = games.sortedByDescending { it.playtimeMinutes }.take(TOP_N)
            SteamResult.Ok(top, total)
        }.getOrElse { SteamResult.Fail("解析 Steam 数据失败：${it.message ?: ""}") }
    }
}
