package com.brian.grokbar.data

import org.json.JSONArray
import org.json.JSONObject

enum class UsageProduct(
    val id: Int,
    val label: String
) {
    BUILD(2, "Grok Build"),
    CHAT(4, "Chat"),
    VOICE(6, "Voice"),
    IMAGINE(5, "Imagine"),
    AUTOMATIONS(3, "Automations");

    companion object {
        val gaugeOrder: List<UsageProduct> = listOf(BUILD, CHAT, VOICE, IMAGINE, AUTOMATIONS)

        fun fromId(id: Int): UsageProduct? = entries.find { it.id == id }

        fun labelFor(id: Int): String = when (id) {
            0 -> "3rd Party"
            1 -> "API"
            3 -> "Automations"
            else -> fromId(id)?.label ?: "Product $id"
        }
    }
}

data class ProductShare(
    val id: Int,
    val label: String,
    val percent: Float
)

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
                ProductShare(UsageProduct.AUTOMATIONS.id, UsageProduct.AUTOMATIONS.label, 3f)
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
