package com.txdost.gguf

// ─────────────────────────────────────────────────────────────
//  GgufModelData — Data classes mirroring Flutter's models
//  AiModelInfo  → catalog entry
//  DownloadStatus / GgufDownloadState → live download tracking
// ─────────────────────────────────────────────────────────────

data class AiModelInfo(
    val id: String,
    val name: String,
    val filename: String,
    val url: String,
    val sizeGb: Double,
    val minRamGb: Int,
    val label: String,          // "UNCENSORED" | "STANDARD"
    val badge: String,          // "RECOMMENDED", "LIGHTWEIGHT", etc.
    val systemPrompt: String,
    val recommended: Boolean = false,
    val isCustom: Boolean = false
) {
    companion object {
        fun fromJson(map: Map<String, Any?>): AiModelInfo = AiModelInfo(
            id           = map["id"] as? String ?: "",
            name         = map["name"] as? String ?: "",
            filename     = map["filename"] as? String ?: "",
            url          = map["url"] as? String ?: "",
            sizeGb       = (map["sizeGb"] as? Number)?.toDouble() ?: 0.0,
            minRamGb     = (map["minRamGb"] as? Number)?.toInt() ?: 4,
            label        = map["label"] as? String ?: "",
            badge        = map["badge"] as? String ?: "",
            systemPrompt = map["systemPrompt"] as? String ?: "",
            recommended  = map["recommended"] as? Boolean ?: false,
            isCustom     = map["isCustom"] as? Boolean ?: false
        )
    }

    fun toJsonMap(): Map<String, Any?> = mapOf(
        "id" to id, "name" to name, "filename" to filename, "url" to url,
        "sizeGb" to sizeGb, "minRamGb" to minRamGb, "label" to label,
        "badge" to badge, "systemPrompt" to systemPrompt,
        "recommended" to recommended, "isCustom" to true
    )
}

enum class DownloadStatus { IDLE, DOWNLOADING, PAUSED, DONE, FAILED, CANCELLED }

data class GgufDownloadState(
    val filename: String,
    var totalBytes: Long = 0L,
    var receivedBytes: Long = 0L,
    var speedBytesPerSec: Double = 0.0,
    var status: DownloadStatus = DownloadStatus.DOWNLOADING,
    var errorMsg: String = ""
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) (receivedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val progressPercent: Int get() = (progressFraction * 100).toInt()

    val speedLabel: String
        get() {
            val s = speedBytesPerSec
            return when {
                s >= 1_000_000 -> "${"%.1f".format(s / 1_000_000)} MB/s"
                s >= 1_000     -> "${"%.0f".format(s / 1_000)} KB/s"
                else           -> "${s.toInt()} B/s"
            }
        }

    val etaLabel: String
        get() {
            val remaining = totalBytes - receivedBytes
            if (speedBytesPerSec <= 0 || remaining <= 0) return "--"
            val secs = (remaining / speedBytesPerSec).toLong()
            return when {
                secs < 60   -> "${secs}s"
                secs < 3600 -> "${secs / 60}m ${secs % 60}s"
                else        -> "${secs / 3600}h ${(secs % 3600) / 60}m"
            }
        }

    val isActive: Boolean get() = status == DownloadStatus.DOWNLOADING
    val isCancelled: Boolean get() = status == DownloadStatus.CANCELLED
}
