package com.hourlysteps.surfaces

import android.content.ComponentName
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Arc
import androidx.wear.protolayout.LayoutElementBuilders.ArcLine
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.hourlysteps.HourlyStepsApp
import com.hourlysteps.domain.DayStatus
import com.hourlysteps.ui.MainActivity
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers

private const val RESOURCES_VERSION = "1"
private const val OUTER_THICKNESS_DP = 9f
private const val INNER_THICKNESS_DP = 8f
private const val RING_GAP_DP = 4f
private const val MAX_FRESHNESS_MS = 5 * 60_000L
private const val MIN_FRESHNESS_MS = 60_000L

/** The swipe-from-the-watch-face card: today's total in the outer ring, this slot in the inner. */
class HourlyStepsTileService : TileService() {

    private val container get() = (application as HourlyStepsApp).container

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.IO) {
            val status = container.dayStatusProvider.current()
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(freshnessMillis(status))
                .setTileTimeline(Timeline.fromLayoutElement(tileLayout(ringContent(status))))
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<Resources> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.IO) {
            Resources.Builder().setVersion(RESOURCES_VERSION).build()
        }

    /** Refresh when the slot rolls over, so a still wearer doesn't see last hour's count. */
    private fun freshnessMillis(status: DayStatus): Long {
        val now = ZonedDateTime.now(container.clock)
        val untilSlotEnd = status.slot?.let { Duration.between(now, it.endAt(now.zone)).toMillis() } ?: MAX_FRESHNESS_MS
        return untilSlotEnd.coerceIn(MIN_FRESHNESS_MS, MAX_FRESHNESS_MS)
    }

    private fun tileLayout(content: RingContent): LayoutElement {
        val open = ModifiersBuilders.Clickable.Builder()
            .setId("open_app")
            .setOnClick(ActionBuilders.launchAction(ComponentName(this, MainActivity::class.java)))
            .build()
        val outerColor = if (content.goalMet) RingColors.GOAL_MET else RingColors.TODAY

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(open).build())
            .addContent(ring(content.todayProgress, outerColor, OUTER_THICKNESS_DP))
            .addContent(
                Box.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .setModifiers(paddingAll(OUTER_THICKNESS_DP + RING_GAP_DP))
                    .addContent(ring(content.slotProgress, RingColors.SLOT, INNER_THICKNESS_DP))
                    .build(),
            )
            .addContent(textColumn(content, outerColor))
            .build()
    }

    /** A full-circle track with the progress arc drawn over it, starting at 12 o'clock. */
    private fun ring(progress: Float, color: Long, thickness: Float): LayoutElement {
        val box = Box.Builder().setWidth(expand()).setHeight(expand())
            .addContent(arc(360f, RingColors.TRACK, thickness))
        if (progress > 0f) box.addContent(arc(360f * progress, color, thickness))
        return box.build()
    }

    private fun arc(lengthDegrees: Float, color: Long, thickness: Float): LayoutElement =
        Arc.Builder()
            .setAnchorAngle(degrees(0f))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .addContent(
                ArcLine.Builder()
                    .setLength(degrees(lengthDegrees))
                    .setThickness(dp(thickness))
                    .setColor(argb(color.toInt()))
                    .setStrokeCap(LayoutElementBuilders.STROKE_CAP_ROUND)
                    .build(),
            )
            .build()

    private fun textColumn(content: RingContent, outerColor: Long): LayoutElement {
        val column = Column.Builder().setWidth(wrap()).setHeight(wrap())
        content.title?.let { column.addContent(text(it, 13f, outerColor, bold = true)) }
        column.addContent(text(content.value, 34f, RingColors.TEXT, bold = true))
        content.valueCaption?.let {
            column.addContent(text(it, 12f, if (content.captionIsSlot) RingColors.SLOT else RingColors.TEXT_DIM))
        }
        content.todayLine?.let { column.addContent(text(it, 12f, outerColor)) }
        (content.catchUpLine ?: content.detailLine)?.let { column.addContent(text(it, 11f, RingColors.TEXT_DIM)) }
        return column.build()
    }

    private fun text(value: String, size: Float, color: Long, bold: Boolean = false): LayoutElement =
        Text.Builder()
            .setText(value)
            .setMaxLines(1)
            .setFontStyle(
                FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color.toInt()))
                    .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                    .build(),
            )
            .build()

    private fun paddingAll(value: Float): ModifiersBuilders.Modifiers =
        ModifiersBuilders.Modifiers.Builder()
            .setPadding(ModifiersBuilders.Padding.Builder().setAll(dp(value)).build())
            .build()
}
