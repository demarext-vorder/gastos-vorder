package com.vorder.gastos

import android.content.Context
import android.content.Intent

object Prefs {
    private val keywords = listOf(
        "mercado pago", "mercadopago", "uala", "naranja", "brubank", "modo", "cuenta dni",
        "personal pay", "lemon", "belo", "prex", "galicia", "santander", "bbva", "macro", "banco",
        "bank", "bna", "bapro", "icbc", "hsbc", "supervielle", "patagonia", "itau", "credicoop",
        "hipotecario", "reba", "claro pay", "pay", "wallet", "billetera", "visa", "mastercard",
        "amex", "cencosud", "openbank", "wilobank", "astropay", "paypal", "cocos", "ripio",
        "buenbit", "binance", "takenos", "dolarapp"
    )

    private fun sp(ctx: Context) = ctx.getSharedPreferences("gastos_prefs", Context.MODE_PRIVATE)

    fun looksFinancial(label: String, pkg: String): Boolean {
        val n = Parser.norm(label)
        return keywords.any { n.contains(it) } || pkg.lowercase().contains("mercadopago")
    }

    /** Apps con ícono de lanzador (excepto esta), ordenadas por nombre. */
    fun launcherApps(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .filter { it.second != ctx.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
    }

    fun detect(ctx: Context): Set<String> =
        launcherApps(ctx).filter { looksFinancial(it.first, it.second) }.map { it.second }.toSet()

    fun monitored(ctx: Context): Set<String> {
        val s = sp(ctx).getStringSet("apps", null)
        if (s != null) return HashSet(s)
        val d = detect(ctx)
        setMonitored(ctx, d)
        return d
    }

    fun setMonitored(ctx: Context, set: Set<String>) {
        sp(ctx).edit().putStringSet("apps", HashSet(set)).apply()
    }

    fun labelOf(ctx: Context, pkg: String): String {
        return try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }
}
