package com.example.earthonline.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

/** 一次更新检查的结果 */
data class UpdateInfo(
    /** 最新版本号（已去掉 v 前缀，例如 1.2.2） */
    val version: String,
    /** Release 页面地址（浏览器打开） */
    val releaseUrl: String,
    /** 更新说明（GitHub Release body，截断到 400 字，避免弹窗变成一堵墙） */
    val notes: String,
    /** 直链 APK 地址（assets 里第一个 .apk；没有则为 null，只能跳页面下载） */
    val apkUrl: String?
) {
    val hasUpdate: Boolean get() = version.isNotBlank()
}

/**
 * 应用内检查更新。
 *
 * 数据源：GitHub Releases 的 latest 接口（公开只读，不需要 token）。
 * 本仓库只负责「读」最新版本号 / 更新说明 / APK 直链；真正的「下载 + 引导安装」
 * 由 SettingsScreen 在用户点「下载并安装」后处理（系统 DownloadManager 下载、
 * 下载完成广播触发安装意图，Android 8.0+ 先申请「未知来源」权限）。
 *
 * 失败路径必须是「友好提示」而不是崩溃或空白：无外网、被墙、仓库不存在、
 * 触发 GitHub 未鉴权限流（60 次/小时/IP）都会走到失败分支，统一交给调用方提示。
 */
@Singleton
class UpdateRepository @Inject constructor(
    private val client: OkHttpClient
) {

    /**
     * v1.0.6：检查更新也走「官方直链 + 备用镜像」多线路。
     * api.github.com 在国内网络同样不稳（超时 / TLS 被拦 / 直接不可达），
     * 单线路失败就等于「检查更新没反应」。这里逐条尝试，任一成功即用其返回。
     */
    private val apiMirrors = listOf(
        "https://ghproxy.net/",
        "https://ghfast.top/",
        "https://gh-proxy.com/",
        "https://gh-proxy.net/"
    )

    suspend fun checkLatest(owner: String, repo: String): Result<UpdateInfo> =
        withContext(Dispatchers.IO) {
            val official = "https://api.github.com/repos/$owner/$repo/releases/latest"
            val candidates = listOf(official) + apiMirrors.map { it + official }
            var lastError: String? = null
            for (url in candidates) {
                val r = runCatching { fetchLatest(url) }
                if (r.isSuccess) return@withContext Result.success(r.getOrThrow())
                lastError = r.exceptionOrNull()?.message ?: "无法连接更新服务器"
            }
            Result.failure(Exception(lastError ?: "无法连接更新服务器"))
        }

    /** 单条线路拉取 latest Release（失败直接抛，由调用方决定换线） */
    private fun fetchLatest(url: String): UpdateInfo {
        // 国内直连 GitHub 不稳：给整次调用设超时，避免无限挂起。
        val timed = client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "EarthOnline-Android")
            .get()
            .build()
        timed.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("更新服务器返回 ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw java.io.IOException("更新服务器返回空内容")
            val json = JSONObject(body)
            val info = UpdateInfo(
                version = json.optString("tag_name").removePrefix("v").trim(),
                releaseUrl = json.optString("html_url"),
                notes = json.optString("body").trim().take(400),
                apkUrl = pickApk(json.optJSONArray("assets"))
            )
            if (info.version.isBlank()) throw java.io.IOException("更新服务器未返回版本号")
            return info
        }
    }

    /** assets 里挑第一个 .apk 直链（Release 里可能同时有 apk / json / 源码包） */
    private fun pickApk(assets: JSONArray?): String? {
        if (assets == null) return null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                return a.optString("browser_download_url").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    /**
     * 版本号比较：按 . 分段逐位比数字。
     * 不能直接字符串比较 —— "1.10.0" < "1.9.0" 在字典序下是错的。
     * 非数字段（如 -beta）只取其前导数字，解析不了就当 0，保证比较永远有结果、不抛异常。
     */
    fun isNewer(latest: String, current: String): Boolean {
        val a = segments(latest)
        val b = segments(current)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun segments(v: String): List<Int> =
        v.split('.').map { part -> part.trim().takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}
