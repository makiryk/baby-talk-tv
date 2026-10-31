package com.babytalk.tv.audio

enum class UtteranceIntent {
    WORD_MAMMA, WORD_WANWAN, WORD_BAA, WORD_PAPA, WORD_MAMA, WORD_BUUBUU,
    WORD_GREETING, WORD_BYE, WORD_NENNE,
    BABBLE_VOWEL, BABBLE_REPETITION, GENERAL_HAPPY, GENERAL_FUSS, GENERAL_NEUTRAL
}

enum class UnderstandingConfidence { HIGH, MEDIUM, LOW }
enum class UnderstandingSource { ACOUSTIC, STT, FALLBACK }

data class UtteranceUnderstanding(
    val intent: UtteranceIntent,
    val recognizedText: String?,
    val detectedKeyword: String?,
    val tone: VoiceTone,
    val confidence: UnderstandingConfidence,
    val source: UnderstandingSource,
    val durationMs: Long
)
