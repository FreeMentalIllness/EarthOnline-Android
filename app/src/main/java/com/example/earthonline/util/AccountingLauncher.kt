package com.example.earthonline.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * 记账：外链跳转（死命令 #1 回归）。
 *
 * v1.0.6.9 曾在应用内塞了一个账本页，深度反馈要求撤销 —— 原生端不内置记账，
 * 速览「记账」必须回到「跳到外部记账 App / 下载页」的外链模式。
 *
 * 行为：
 *  ① 若配置了 [ACCOUNTING_TARGET_PACKAGE] 且本机已安装该记账 App，则直接用
 *     [Intent] 拉起它（即用户要求的「Intent 启动指定第三方记账 App」）；
 *  ② 否则回落到浏览器打开 [ACCOUNTING_DOWNLOAD_URL]（恢复 v1.0.1 的外链跳转，
 *     git 历史里的「Verifin 下载引导」）。
 *
 * 包名默认留空 ⇒ 始终走 ②，与历史行为完全一致，且不臆造不存在的包名。
 * 想改用「直接拉起某个已装记账 App」时，把包名填进 [ACCOUNTING_TARGET_PACKAGE] 即可。
 */
private const val ACCOUNTING_TARGET_PACKAGE: String = ""
private const val ACCOUNTING_DOWNLOAD_URL =
    "https://github.com/LumiDesk/verifin/releases"

fun launchAccounting(context: Context) {
    val pkg = ACCOUNTING_TARGET_PACKAGE
    if (pkg.isNotBlank()) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolved = runCatching {
            context.packageManager?.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        }.getOrDefault(false)
        if (resolved) {
            runCatching { context.startActivity(intent) }.onFailure {
                // 极少数 ROM 拉起失败 → 回落浏览器外链
                openUrlInBrowser(context, ACCOUNTING_DOWNLOAD_URL)
            }
            return
        }
    }
    // 默认 / 兜底：外链跳转（恢复 v1.0.1 行为）
    openUrlInBrowser(context, ACCOUNTING_DOWNLOAD_URL)
}
