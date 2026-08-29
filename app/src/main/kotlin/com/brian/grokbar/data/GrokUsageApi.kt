package com.brian.grokbar.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class GrokUsageApi {

    data class Auth(
        val cookie: String? = null,
        val bearer: String? = null
    ) {
        val hasAny: Boolean get() = !cookie.isNullOrBlank() || !bearer.isNullOrBlank()
    }

    fun fetch(auth: Auth): UsageSnapshot {
        val errors = mutableListOf<String>()

        if (!auth.bearer.isNullOrBlank()) {
            runCatching { fetchBillingJson(auth.bearer) }
                .onSuccess { return it }
                .onFailure { errors += "billing: ${it.message}" }

            runCatching { fetchSettingsPlan(auth.bearer) }
        }

        if (!auth.cookie.isNullOrBlank()) {
            runCatching { fetchCreditsGrpc(auth.cookie) }
                .onSuccess { decoded ->
                    val plan = runCatching { fetchPlanFromSubscriptions(auth.cookie) }.getOrNull()
                    return UsageSnapshot(
                        usedPercent = decoded.usedPercent,
                        resetAtEpochMs = decoded.resetAtEpochMs,
                        planName = plan,
                        products = decoded.products,
                        fetchedAtEpochMs = System.currentTimeMillis(),
                        isDemo = false
                    )
                }
                .onFailure { errors += "grpc: ${it.message}" }
        }

        throw IllegalStateException(errors.joinToString("; ").ifBlank { "Not signed in" })
    }

    fun fetchCreditsGrpc(cookie: String): CreditsProto.Decoded {
        val body = ByteArray(5)
        val bytes = requestBytes(
            url = CREDITS_URL,
            method = "POST",
            headers = mapOf(
                "Content-Type" to "application/grpc-web+proto",
                "X-Grpc-Web" to "1",
                "connect-protocol-version" to "1",
                "Cookie" to cookie,
                "Origin" to "https://grok.com",
                "Referer" to "https://grok.com/"
            ),
            body = body
        )
        return CreditsProto.parseGrpcWeb(bytes)
            ?: throw IllegalStateException("Could not parse usage response")
    }

    private fun fetchBillingJson(bearer: String): UsageSnapshot {
        val text = requestText(
            url = BILLING_URL,
            method = "GET",
            headers = mapOf(
                "Authorization" to "Bearer $bearer",
                "Accept" to "application/json",
                "x-xai-token-auth" to "xai-grok-cli",
                "User-Agent" to "xai-grok-cli"
            )
        )
        val root = JSONObject(text)
        val config = root.optJSONObject("config") ?: root
        val used = number(config, "creditUsagePercent", "credit_usage_percent")
            ?: ratioPercent(config)
            ?: 0.0
        val resetMs = periodEndMillis(config)
        val products = readProducts(config)
        val plan = runCatching { fetchSettingsPlan(bearer) }.getOrNull()
        return UsageSnapshot(
            usedPercent = used.toFloat().coerceIn(0f, 100f),
            resetAtEpochMs = resetMs,
            planName = plan,
            products = products,
            fetchedAtEpochMs = System.currentTimeMillis(),
            isDemo = false
        )
    }

    private fun fetchSettingsPlan(bearer: String): String? {
        val text = requestText(
            url = SETTINGS_URL,
            method = "GET",
            headers = mapOf(
                "Authorization" to "Bearer $bearer",
                "Accept" to "application/json",
                "x-xai-token-auth" to "xai-grok-cli",
                "User-Agent" to "xai-grok-cli"
            )
        )
        val obj = JSONObject(text)
        return obj.optString("subscription_tier_display").ifBlank { null }
            ?: obj.optString("subscriptionTierDisplay").ifBlank { null }
    }

    private fun fetchPlanFromSubscriptions(cookie: String): String? {
        val text = requestText(
            url = SUBSCRIPTIONS_URL,
            method = "GET",
            headers = mapOf(
                "Accept" to "application/json",
                "Cookie" to cookie,
                "Origin" to "https://grok.com",
                "Referer" to "https://grok.com/"
            )
        )
        val root = JSONObject(text)
        val list = root.optJSONArray("subscriptions") ?: return null
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            val status = item.optString("status")
            if (status.contains("ACTIVE", ignoreCase = true)) {
                return item.optString("tier").ifBlank { null }
                    ?: item.optString("planName").ifBlank { null }
                    ?: item.optString("name").ifBlank { null }
                    ?: "SuperGrok"
            }
        }
        return null
    }

    private fun readProducts(config: JSONObject): List<ProductShare> {
        val arr = config.optJSONArray("productUsage")
            ?: config.optJSONArray("product_usage")
            ?: JSONArray()
        val out = mutableListOf<ProductShare>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val id = p.optInt("product", p.optInt("productId", p.optInt("id", -1)))
            if (id < 0) continue
            val percent = number(p, "usagePercent", "usage_percent", "percent") ?: 0.0
            if (percent <= 0.0) continue
            out.add(ProductShare(id, UsageProduct.labelFor(id), percent.toFloat()))
        }
        return out.sortedByDescending { it.percent }
    }

    private fun periodEndMillis(config: JSONObject): Long? {
        val period = config.optJSONObject("currentPeriod")
            ?: config.optJSONObject("current_period")
        val end = period?.opt("end") ?: config.opt("billingPeriodEnd") ?: config.opt("billing_period_end")
        return parseTime(end)
    }

    private fun parseTime(value: Any?): Long? {
        return when (value) {
            is String -> runCatching {
                java.time.Instant.parse(value).toEpochMilli()
            }.getOrNull()
            is Number -> {
                val n = value.toLong()
                if (n > 10_000_000_000L) n else n * 1000L
            }
            is JSONObject -> {
                val seconds = value.optLong("seconds", value.optLong("val", 0L))
                if (seconds > 0) seconds * 1000L else parseTime(value.opt("end"))
            }
            else -> null
        }
    }

    private fun ratioPercent(config: JSONObject): Double? {
        val used = nestedVal(config, "onDemandUsed", "on_demand_used")
        val cap = nestedVal(config, "onDemandCap", "on_demand_cap")
        if (used == null || cap == null || cap == 0.0) return null
        return (used / cap) * 100.0
    }

    private fun nestedVal(obj: JSONObject, vararg keys: String): Double? {
        for (key in keys) {
            val child = obj.opt(key) ?: continue
            when (child) {
                is Number -> return child.toDouble()
                is JSONObject -> {
                    val v = child.opt("val") ?: child.opt("value")
                    if (v is Number) return v.toDouble()
                }
            }
        }
        return null
    }

    private fun number(obj: JSONObject, vararg keys: String): Double? {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val v = obj.opt(key)
            if (v is Number) return v.toDouble()
            if (v is String) return v.toDoubleOrNull()
        }
        return null
    }

    private fun requestText(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: ByteArray? = null
    ): String {
        return requestBytes(url, method, headers, body).toString(StandardCharsets.UTF_8)
    }

    private fun requestBytes(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: ByteArray? = null
    ): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            doInput = true
            if (body != null) {
                doOutput = true
                setFixedLengthStreamingMode(body.size)
            }
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }
        try {
            if (body != null) {
                conn.outputStream.use { it.write(body) }
            }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val bytes = stream?.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            } ?: ByteArray(0)
            if (conn.responseCode !in 200..299) {
                val snippet = bytes.toString(StandardCharsets.UTF_8).take(180)
                throw IllegalStateException("HTTP ${conn.responseCode} $snippet")
            }
            return bytes
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val CREDITS_URL = "https://grok.com/grok_api_v2.GrokBuildBilling/GetGrokCreditsConfig"
        const val BILLING_URL = "https://cli-chat-proxy.grok.com/v1/billing?format=credits"
        const val SETTINGS_URL = "https://cli-chat-proxy.grok.com/v1/settings"
        const val SUBSCRIPTIONS_URL = "https://grok.com/rest/subscriptions"
        const val USAGE_PAGE = "https://grok.com/?_s=usage"
        const val LOGIN_PAGE = "https://accounts.x.ai/sign-in?redirect=https%3A%2F%2Fgrok.com%2F%3F_s%3Dusage"
    }
}
