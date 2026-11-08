package app.stegvis.wear

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Always-on (ambient) state, written by MainActivity's AmbientLifecycleObserver and read by the UI. */
object AmbientState {
    var ambient by mutableStateOf(false)
    var tick by mutableLongStateOf(0L) // bumped once a minute while ambient
}
