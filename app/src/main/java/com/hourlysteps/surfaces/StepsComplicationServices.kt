package com.hourlysteps.surfaces

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.hourlysteps.HourlyStepsApp
import com.hourlysteps.domain.DayStatus
import com.hourlysteps.ui.MainActivity

/** Base for the watch-face data sources; each picks which number it shows. */
abstract class StepsComplicationService : SuspendingComplicationDataSourceService() {

    protected abstract fun content(status: DayStatus): ComplicationContent
    protected abstract val previewContent: ComplicationContent

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val status = (application as HourlyStepsApp).container.dayStatusProvider.current()
        return build(request.complicationType, content(status))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, previewContent)

    private fun build(type: ComplicationType, content: ComplicationContent): ComplicationData? {
        val description = plain(content.longText)
        val tap = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return when (type) {
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(content.value, content.min, content.max, description)
                    .setText(plain(content.shortText))
                    .setTapAction(tap)
                    .build()
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(plain(content.shortText), description)
                    .apply { content.shortTitle?.let { setTitle(plain(it)) } }
                    .setTapAction(tap)
                    .build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(plain(content.longText), description)
                    .setTapAction(tap)
                    .build()
            else -> null
        }
    }

    private fun plain(text: String) = PlainComplicationText.Builder(text).build()
}

/** This slot's steps against its target. */
class SlotComplicationService : StepsComplicationService() {
    override fun content(status: DayStatus) = slotComplication(status)
    override val previewContent = ComplicationContent(412f, 584f, "412", "of 584", "412 of 584 this hour")
}

/** Today's total against the daily goal. */
class TodayComplicationService : StepsComplicationService() {
    override fun content(status: DayStatus) = todayComplication(status)
    override val previewContent = ComplicationContent(3100f, 7000f, "3,100", "today", "3,100 of 7,000 today")
}

/** How many reminders today has. */
class ReminderCountComplicationService : StepsComplicationService() {
    override fun content(status: DayStatus) = reminderCountComplication(status)
    override val previewContent = ComplicationContent(12f, 12f, "12", "reminders", "12 reminders today")
}
