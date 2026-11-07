package app.stegvis.wear

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Session, goal and steps live in encrypted preferences. Falls back to memory only, never to plain files. */
class PrefsKv(context: Context) : KeyValueStore {
    private val prefs = try {
        EncryptedSharedPreferences.create(
            context,
            "stegvis_secure",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        Log.w("Stegvis", "Encrypted storage unavailable; keeping data for this run only")
        null
    }
    private val memory = MemoryStore()

    override fun get(key: String): String? = prefs?.getString(key, null) ?: memory.get(key)
    override fun put(key: String, value: String?) {
        memory.put(key, value)
        prefs?.edit()?.apply { if (value == null) remove(key) else putString(key, value) }?.apply()
    }
}

/** One-shot read of the hardware step counter (total steps since boot). Null when there is no counter or no permission. */
fun readStepTotal(context: Context, timeoutMs: Long = 4000): Long? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) return null
    val sm = context.getSystemService(SensorManager::class.java) ?: return null
    val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
    val latch = CountDownLatch(1)
    val result = java.util.concurrent.atomic.AtomicReference<Long?>(null)
    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            result.set(event.values[0].toLong())
            latch.countDown()
        }
        override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
    }
    if (!sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) return null
    try {
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    } finally {
        sm.unregisterListener(listener)
    }
    return result.get()
}

/** App-wide services. Built per call, so a worker, tile, complication and the activity share the same storage. */
class Services(context: Context) {
    private val app = context.applicationContext
    val vault = Vault(PrefsKv(app))
    val coordinator = Coordinator(
        SupabaseAuth(UrlHttp(), BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY),
        StegvisApi(UrlHttp()),
        vault,
    )
    val hasSignInConfig: Boolean get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    /** Reads the sensor, updates today's steps and history, and returns today's steps. */
    fun updateSteps(now: Long = System.currentTimeMillis()): Int {
        val total = readStepTotal(app)
        if (total != null) {
            val day = dayKey(now)
            val next = addReading(vault.sensor, total, day, now)
            vault.sensor = next
            vault.recordSteps(day, next.steps)
            refreshSurfaces()
        }
        return vault.todaySteps(now)
    }

    /** Asks the tile and the complication to redraw with the newest value. */
    fun refreshSurfaces() {
        runCatching { TileService.getUpdater(app).requestUpdate(StepTileService::class.java) }
        runCatching {
            ComplicationDataSourceUpdateRequester.create(app, ComponentName(app, StepComplicationService::class.java)).requestUpdateAll()
        }
    }

    /** Updates steps and posts them. Never throws. */
    fun syncNow(): SyncResult = try {
        val hasPermission = ContextCompat.checkSelfPermission(app, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val steps = updateSteps()
        if (steps == 0 && !hasPermission) SyncResult.NO_PERMISSION
        else if (steps == 0) (if (coordinator.access() is Access.Token) SyncResult.OK else coordinator.syncResult(0))
        else coordinator.syncResult(steps)
    } catch (e: Exception) {
        SyncResult.FAILED
    }

    /** Asks the phone for its token (the Data Layer keeps the last value on the watch). Returns true when a token was found. */
    fun pullPhoneToken(): Boolean {
        val uri = Uri.Builder().scheme("wear").authority("*").path(TOKEN_PATH).build()
        val buffer = Tasks.await(Wearable.getDataClient(app).getDataItems(uri), 8, TimeUnit.SECONDS)
        try {
            for (i in 0 until buffer.count) {
                val item = buffer.get(i)
                if (item.uri.path == TOKEN_PATH) {
                    val token = DataMapItem.fromDataItem(item).dataMap.getString("token", "")
                    vault.savePhoneToken(token, System.currentTimeMillis())
                    return token.isNotBlank()
                }
            }
        } finally {
            buffer.release()
        }
        return false
    }
}
