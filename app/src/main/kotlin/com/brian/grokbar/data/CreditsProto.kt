package com.brian.grokbar.data

/**
 * Minimal protobuf / gRPC-web decoder for GetGrokCreditsConfig.
 *
 * Response layout (observed on grok.com):
 *   field 1 = GrokCreditsConfig message
 *     field 1  float  credit_usage_percent
 *     field 5  google.protobuf.Timestamp  period end (seconds)
 *     field 7  repeated ProductUsage { id:1, percent:2 float }
 *     field 8  current period { start:2 timestamp, end:3 timestamp }
 */
object CreditsProto {

    data class Decoded(
        val usedPercent: Float,
        val resetAtEpochMs: Long?,
        val products: List<ProductShare>
    )

    fun parseGrpcWeb(bytes: ByteArray): Decoded? {
        val payload = unwrapGrpcWeb(bytes) ?: return null
        val root = decode(payload)
        val configBytes = firstBytes(root, 1) ?: payload
        val config = decode(configBytes)

        val used = firstFloat(config, 1)
            ?: firstVarint(config, 1)?.toFloat()

        val resetFromField5 = timestampMillis(firstBytes(config, 5))
        val period = firstBytes(config, 8)?.let { decode(it) }
        val resetFromPeriod = timestampMillis(firstBytes(period, 3))
            ?: timestampMillis(firstBytes(period, 2))
        val resetAt = resetFromField5 ?: resetFromPeriod

        val productEntries = config[7].orEmpty()
        val products = productEntries.mapNotNull { entry ->
            val raw = entry as? ByteArray ?: return@mapNotNull null
            val parsed = decode(raw)
            val id = firstVarint(parsed, 1)?.toInt() ?: return@mapNotNull null
            val percent = firstFloat(parsed, 2) ?: firstVarint(parsed, 2)?.toFloat() ?: 0f
            ProductShare(id = id, label = UsageProduct.labelFor(id), percent = percent)
        }.filter { it.percent > 0f }
            .sortedByDescending { it.percent }

        if (used == null && resetAt == null && productEntries.isEmpty()) {
            return null
        }
        return Decoded(
            usedPercent = (used ?: 0f).coerceIn(0f, 100f),
            resetAtEpochMs = resetAt,
            products = products
        )
    }

    private fun unwrapGrpcWeb(bytes: ByteArray): ByteArray? {
        if (bytes.isEmpty()) return null
        var at = 0
        while (at + 5 <= bytes.size) {
            val flag = bytes[at].toInt() and 0xFF
            val len = u32be(bytes, at + 1)
            val start = at + 5
            val end = start + len
            if (end > bytes.size) break
            if (flag and 0x80 == 0) {
                return bytes.copyOfRange(start, end)
            }
            at = end
        }
        return if (bytes.size > 8) bytes else null
    }

    private data class FieldBag(val fields: Map<Int, List<Any>>) {
        operator fun get(field: Int): List<Any>? = fields[field]
    }

    private fun decode(bytes: ByteArray): FieldBag {
        val out = mutableMapOf<Int, MutableList<Any>>()
        var at = 0
        fun varint(): Long {
            var value = 0L
            var shift = 0
            while (at < bytes.size) {
                val b = bytes[at++].toInt() and 0xFF
                value = value or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) break
                shift += 7
                if (shift > 63) break
            }
            return value
        }
        while (at < bytes.size) {
            val key = varint()
            val field = (key ushr 3).toInt()
            val wire = (key and 7L).toInt()
            if (field == 0) break
            val bucket = out.getOrPut(field) { mutableListOf() }
            when (wire) {
                0 -> bucket.add(varint())
                1 -> {
                    if (at + 8 > bytes.size) break
                    at += 8
                }
                2 -> {
                    val len = varint().toInt()
                    if (len < 0 || at + len > bytes.size) break
                    bucket.add(bytes.copyOfRange(at, at + len))
                    at += len
                }
                5 -> {
                    if (at + 4 > bytes.size) break
                    val bits = (bytes[at].toInt() and 0xFF) or
                        ((bytes[at + 1].toInt() and 0xFF) shl 8) or
                        ((bytes[at + 2].toInt() and 0xFF) shl 16) or
                        ((bytes[at + 3].toInt() and 0xFF) shl 24)
                    bucket.add(Float.fromBits(bits))
                    at += 4
                }
                else -> break
            }
        }
        return FieldBag(out)
    }

    private fun firstBytes(bag: FieldBag?, field: Int): ByteArray? =
        bag?.get(field)?.firstOrNull() as? ByteArray

    private fun firstFloat(bag: FieldBag?, field: Int): Float? =
        bag?.get(field)?.firstOrNull() as? Float

    private fun firstVarint(bag: FieldBag?, field: Int): Long? =
        bag?.get(field)?.firstOrNull() as? Long

    private fun timestampMillis(bytes: ByteArray?): Long? {
        if (bytes == null) return null
        val msg = decode(bytes)
        val seconds = firstVarint(msg, 1) ?: return null
        if (seconds <= 0L) return null
        val nanos = firstVarint(msg, 2) ?: 0L
        return seconds * 1000L + nanos / 1_000_000L
    }

    private fun u32be(bytes: ByteArray, at: Int): Int {
        return ((bytes[at].toInt() and 0xFF) shl 24) or
            ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or
            (bytes[at + 3].toInt() and 0xFF)
    }
}
