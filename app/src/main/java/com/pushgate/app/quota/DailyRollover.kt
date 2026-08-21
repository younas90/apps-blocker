package com.pushgate.app.quota

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pushgate.app.data.repo.BlockRepository
import com.pushgate.app.util.TimeKeys
import java.util.concurrent.TimeUnit

/**
 * Fires at the daily rollover hour. Its only real jobs are to drop stale grants so a two-minute
 * unlock bought at 03:59 cannot leak into the new day's ledger, and to keep the database trimmed.
 *
 * The budget itself needs no reset: usage is keyed by date, so a new day simply has no row yet.
 */
object DailyRollover {

    private const val WORK_NAME = "pushgate-daily-rollover"

    fun schedule(context: Context) {
        val delay = TimeKeys.millisUntilNextRollover(System.currentTimeMillis())
        val request = OneTimeWorkRequestBuilder<DailyRolloverWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().build())
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}

class DailyRolloverWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        runCatching {
            val repo = BlockRepository.get(applicationContext)
            repo.revokeAllGrants()
            repo.prune()
        }
        // One-shot chaining rather than a periodic worker, so the next run lands exactly on the
        // rollover hour even after a timezone change or a DST shift.
        DailyRollover.schedule(applicationContext)
        return Result.success()
    }
}
