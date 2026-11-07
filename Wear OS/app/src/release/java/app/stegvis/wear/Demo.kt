package app.stegvis.wear

import android.content.Intent

/** Release build: no demo mode. */
object Demo {
    fun config(intent: Intent): DemoConfig? = null
    fun apply(model: AppModel, cfg: DemoConfig) {}
}
