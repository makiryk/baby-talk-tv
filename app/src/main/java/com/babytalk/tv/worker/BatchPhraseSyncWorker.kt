package com.babytalk.tv.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.babytalk.tv.data.db.AppDatabase
import com.babytalk.tv.data.db.BatchSyncJobEntity
import com.babytalk.tv.network.ClaudeBatchApiClient

/**
 * 1日1回またはキャッシュ不足時に Claude Message Batches API と同期するスケジュールワーカー。
 * - M1対応: バッチ状態（SUBMITTED → IN_PROGRESS → ENDED → IMPORTED）の完全管理
 */
class BatchPhraseSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.phraseDao()

        // SharedPreferences 等から Anthropic API Key を取得（未設定時はスキップ）
        val prefs = applicationContext.getSharedPreferences("baby_talk_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("anthropic_api_key", null) ?: return Result.success()

        val client = ClaudeBatchApiClient(apiKey)

        // 1. 未完了ジョブの確認
        val activeJob = dao.getActiveJob()
        if (activeJob != null) {
            val statusResp = client.getBatchStatus(activeJob.batchId)
            if (statusResp != null) {
                if (statusResp.status == "ended" && !statusResp.resultsUrl.isNullOrEmpty()) {
                    // 完了した結果をダウンロードして Room に保存
                    val phrases = client.fetchAndParseResults(statusResp.resultsUrl)
                    if (phrases.isNotEmpty()) {
                        dao.insertPhrases(phrases)
                    }
                    dao.updateJob(activeJob.copy(status = "IMPORTED", resultsUrl = statusResp.resultsUrl))
                } else if (statusResp.status in listOf("expired", "canceled", "failed")) {
                    dao.updateJob(activeJob.copy(status = "FAILED", errorMessage = statusResp.status))
                }
            }
        }

        // 2. 有効キャッシュ数の確認（30件未満なら新規バッチ投入）
        val count = dao.getCount()
        if (count < 30) {
            val createResp = client.createBabyTalkBatch()
            if (createResp != null) {
                dao.insertJob(
                    BatchSyncJobEntity(
                        batchId = createResp.batchId,
                        status = "SUBMITTED"
                    )
                )
            }
        }

        return Result.success()
    }
}
