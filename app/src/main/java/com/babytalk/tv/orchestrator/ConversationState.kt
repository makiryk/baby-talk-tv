package com.babytalk.tv.orchestrator

enum class ConversationState {
    IDLE,       // 待機・見守り中（VAD有効、暗灰呼吸）
    LISTENING,  // 声検知・解析中（淡青点灯）
    SPEAKING,   // 発話中（エコー遮断MUTE、淡緑点灯）
    CLOSING,    // 終了挨拶発話中（エコー遮断MUTE、淡緑点灯）
    STANDBY     // セッション終了・消灯状態（黒）
}
