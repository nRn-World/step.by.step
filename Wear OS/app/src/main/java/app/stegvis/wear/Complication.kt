package app.stegvis.wear

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/** Watch-face complication with today's steps (RANGED_VALUE toward the goal, or SHORT_TEXT). */
class StepComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val services = Services(this)
        val steps = services.vault.todaySteps(System.currentTimeMillis())
        return build(steps, services.vault.goal, request.complicationType)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(7_500, DEFAULT_GOAL, type)

    private fun build(steps: Int, goal: Int, type: ComplicationType): ComplicationData? {
        val label: ComplicationText = PlainComplicationText.Builder(localized().getString(app.stegvis.wear.R.string.complication_name)).build()
        val value: ComplicationText = PlainComplicationText.Builder(formatSteps(steps)).build()
        return when (type) {
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                steps.toFloat(), 0f, goal.toFloat(), label,
            ).setText(value).setTitle(label).build()
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(value, label).build()
            else -> null
        }
    }
}
