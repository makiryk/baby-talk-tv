package com.babytalk.tv.power

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

/**
 * タイマー終了時にテレビをスタンバイ/電源OFFにするためのアクセシビリティサービス。
 * - H1対応: GLOBAL_ACTION_LOCK_SCREEN によるベストエフォートスタンバイ
 * - M8対応: 最小権限（イベント購読なし）、WeakReferenceによるリークフリー参照
 */
class TvPowerAccessibilityService : AccessibilityService() {

    companion object {
        private var serviceRef: WeakReference<TvPowerAccessibilityService>? = null

        fun isAvailable(): Boolean = serviceRef?.get() != null

        fun performLockScreen(): Boolean {
            val service = serviceRef?.get() ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceRef = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // イベント購読は不要（最小権限）
    }

    override fun onInterrupt() {
        // no-op
    }

    override fun onDestroy() {
        serviceRef?.clear()
        serviceRef = null
        super.onDestroy()
    }
}
