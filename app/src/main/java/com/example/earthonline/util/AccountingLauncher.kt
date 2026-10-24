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
 *  ② 否则（未配置包名、或本机未安装）返回 false，由调用方弹出来源选择弹窗
 *     （网盘 + GitHub 两个下载入口）。
 *
 * 包名默认留空 ⇒ 始终走 ②，与历史行为一致，且不臆造不存在的包名。
 * 想改用「直接拉起某个已装记账 App」时，把包名填进 [ACCOUNTING_TARGET_PACKAGE] 即可。
 *
 * @return true = 已成功用 Intent 拉起记账 App；false = 未拉起（需弹出来源选择弹窗）。
 */
private const val ACCOUNTING_TARGET_PACKAGE: String = ""

fun launchAccounting(context: Context): Boolean {
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
            val started = runCatching {
                context.startActivity(intent)
                true
            }.getOrElse { false }
            if (started) return true
        }
    }
    // 未配置包名 / 未安装 / 拉起失败 → 交给调用方弹出来源选择弹窗
    return false
}
