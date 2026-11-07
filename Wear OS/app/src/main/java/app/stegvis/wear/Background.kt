package app.stegvis.wear

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import java.util.concurrent.TimeUnit

const val SYNC_WORK = "stegvis-steps-sync"

/** Reads the step counter and posts steps every ~15 minutes (WorkManager minimum). */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val r = Services(applicationContext).syncNow()
        return if (r == SyncResult.OFFLINE || r == SyncResult.FAILED) Result.retry() else Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(SYNC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** Receives the phone's token over the Data Layer (path /stegvis/token). Only the access token is sent; the watch never refreshes it. */
class TokenService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == TOKEN_PATH) {
                val token = DataMapItem.fromDataItem(event.dataItem).dataMap.getString("token", "")
                Services(this).vault.savePhoneToken(token, System.currentTimeMillis())
            }
        }
    }
}
