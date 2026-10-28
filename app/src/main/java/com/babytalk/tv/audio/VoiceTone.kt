package com.babytalk.tv.audio

enum class VoiceTone {
    COOING_HAPPY,      // ご機嫌・高音・上昇（きゃっ、あーい）
    BABBLING_NEUTRAL,  // 呼びかけ・中音・平坦（ばば、うー）
    FUSS_WHINE         // ぐずり・下降・長音（えーん、うぇー）
}

data class VoiceEvent(
    val tone: VoiceTone,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)
