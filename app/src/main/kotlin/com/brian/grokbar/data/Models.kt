package com.brian.grokbar.data

import org.json.JSONArray
import org.json.JSONObject

enum class UsageProduct(
    val id: Int,
    val label: String
) {
    UNSPECIFIED(0, "Unspecified"),
    API(1, "API"),
    BUILD(2, "Grok Build"),
    PLUGINS(3, "Grok Plugins"),
    CHAT(4, "Chat"),
    IMAGINE(5, "Imagine"),
    VOICE(6, "Voice"),
    APP_BUILDER(7, "App Builder"),
    TASKS(8, "Tasks"),
    BOT(9, "Grok Bot"),
    CURSOR(10, "Cursor"),
    BUGBOT(11, "Bugbot");

    companion object {
        /** Products drawn on the usage ring, in ring order. */
        val gaugeOrder: List<UsageProduct> = listOf(BUILD, CHAT, VOICE, IMAGINE, PLUGINS)

        /** Every billing product, including ones with no reported usage. */
        val legendOrder: List<UsageProduct> = entries

        fun fromId(id: Int): UsageProduct? = entries.find { it.id == id }

        fun labelFor(id: Int): String = fromId(id)?.label ?: "Product $id"
    }
}

data class ProductShare(
    val id: Int,
    val label: String,
    val percent: Float
)

/** Highest usage first. Equal usage is alphabetical by label. */
fun List<ProductShare>.sortedForLegend(): List<ProductShare> {
    return sortedWith(
        compareByDescending<ProductShare> { it.percent }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
    )
}

data class UsageSnapshot(
    val usedPercent: Float,
    val resetAtEpochMs: Long?,
    val planName: String?,
    val products: List<ProductShare>,
    val fetchedAtEpochMs: Long,
    val isDemo: Boolean = false,
    val errorMessage: String? = null
) {
    val remainingPercent: Float get() = (100f - usedPercent).coerceIn(0f, 100f)

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("usedPercent", usedPercent.toDouble())
        obj.put("resetAtEpochMs", resetAtEpochMs ?: JSONObject.NULL)
        obj.put("planName", planName ?: JSONObject.NULL)
        obj.put("fetchedAtEpochMs", fetchedAtEpochMs)
        obj.put("isDemo", isDemo)
        obj.put("errorMessage", errorMessage ?: JSONObject.NULL)
        val arr = JSONArray()
        products.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("label", p.label)
                    .put("percent", p.percent.toDouble())
            )
        }
        obj.put("products", arr)
        return obj.toString()
    }

    companion object {
        fun demo(): UsageSnapshot {
            val products = listOf(
                ProductShare(UsageProduct.BUILD.id, UsageProduct.BUILD.label, 22f),
                ProductShare(UsageProduct.CHAT.id, UsageProduct.CHAT.label, 9f),
                ProductShare(UsageProduct.VOICE.id, UsageProduct.VOICE.label, 5f),
                ProductShare(UsageProduct.IMAGINE.id, UsageProduct.IMAGINE.label, 8f),
                ProductShare(UsageProduct.PLUGINS.id, UsageProduct.PLUGINS.label, 3f)
            )
            return UsageSnapshot(
                usedPercent = 47f,
                resetAtEpochMs = System.currentTimeMillis() + 4L * 24 * 60 * 60 * 1000,
                planName = "SuperGrok",
                products = products,
                fetchedAtEpochMs = System.currentTimeMillis(),
                isDemo = true
            )
        }

        fun fromJson(raw: String?): UsageSnapshot? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val obj = JSONObject(raw)
                val products = mutableListOf<ProductShare>()
                val arr = obj.optJSONArray("products")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val p = arr.getJSONObject(i)
                        products.add(
                            ProductShare(
                                id = p.getInt("id"),
                                label = p.optString("label", UsageProduct.labelFor(p.getInt("id"))),
                                percent = p.getDouble("percent").toFloat()
                            )
                        )
                    }
                }
                UsageSnapshot(
                    usedPercent = obj.getDouble("usedPercent").toFloat(),
                    resetAtEpochMs = if (obj.isNull("resetAtEpochMs")) null else obj.optLong("resetAtEpochMs"),
                    planName = obj.optString("planName").ifBlank { null },
                    products = products,
                    fetchedAtEpochMs = obj.optLong("fetchedAtEpochMs", 0L),
                    isDemo = obj.optBoolean("isDemo", false),
                    errorMessage = obj.optString("errorMessage").ifBlank { null }
                )
            }.getOrNull()
        }
    }
}
