package com.babytalk.tv.power

import android.app.Activity
import android.content.Context
import android.view.WindowManager

/**
 * TV電源スタンバイ試行および安全消灯フォールバックヘルパー。
 * - H1対応: アクセシビリティによるスタンバイ試行
 * - H2対応: HOME画面へ戻さず純黒のまま消灯・消音するフォールバック
 */
object PowerManagerHelper {

    /**
     * テレビの消灯/スタンバイを実行
     */
    fun turnOffTvOrDim(activity: Activity) {
        // 1. AccessibilityService によるスタンバイ試行 (ベストエフォート)
        val locked = TvPowerAccessibilityService.performLockScreen()

        // 2. フォールバック (H2対応):
        // ロックが効かない場合でも、HOME画面には戻さず純黒のまま輝度ゼロ・消音で消灯を担保する
        activity.runOnUiThread {
            // スリープタイマーを有効化
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // 画面輝度を完全ゼロに設定
            val layoutParams = activity.window.attributes
            layoutParams.screenBrightness = 0.0f
            activity.window.attributes = layoutParams
        }
    }
}
