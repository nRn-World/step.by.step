package app.stegvis.wear

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.ambient.AmbientLifecycleObserver
import java.util.Locale

class MainActivity : ComponentActivity() {
    /** Applies the language chosen in Settings; "system" keeps the watch language. */
    override fun attachBaseContext(newBase: Context) {
        val lang = try { Services(newBase).vault.language } catch (e: Exception) { "system" }
        if (lang == "system") {
            super.attachBaseContext(newBase)
            return
        }
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(Locale.forLanguageTag(lang))
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always-on: draw the quiet ambient screen and update it once a minute.
        lifecycle.addObserver(AmbientLifecycleObserver(this, object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) { AmbientState.ambient = true; AmbientState.tick++ }
            override fun onExitAmbient() { AmbientState.ambient = false }
            override fun onUpdateAmbient() { AmbientState.tick++ }
        }))
        SyncWorker.schedule(this)
        setContent { StegvisApp(Demo.config(intent)) }
    }
}
