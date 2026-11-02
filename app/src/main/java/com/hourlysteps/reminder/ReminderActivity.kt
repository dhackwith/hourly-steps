package com.hourlysteps.reminder

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.hourlysteps.HourlyStepsApp
import com.hourlysteps.R
import com.hourlysteps.domain.DayStatus
import com.hourlysteps.domain.ReminderSchedule
import com.hourlysteps.surfaces.paceCheckContent
import com.hourlysteps.ui.HourlyStepsTheme
import com.hourlysteps.ui.LocalAppTheme
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

private const val REFRESH_MS = 3_000L
private const val SHOW_FOR_MS = 30_000L

/**
 * The reminder as a full screen: what's left of this slot, and how the day stands against pace.
 * It follows the steps while open and closes itself once there's nothing left to remind about.
 */
class ReminderActivity : ComponentActivity() {

    private val container get() = (application as HourlyStepsApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        getSystemService(NotificationManager::class.java).cancel(ReminderNotifier.NOTIFICATION_ID)

        setContent {
            HourlyStepsTheme(container.settingsRepository) {
                val statuses = remember {
                    flow {
                        while (true) {
                            emit(container.dayStatusProvider.current())
                            // Steps arrive in batches. This screen is brief, so asking for them is cheap.
                            runCatching { container.stepsRepository.flush() }
                            delay(REFRESH_MS)
                        }
                    }
                }
                val status by statuses.collectAsStateWithLifecycle<DayStatus?>(null)
                val canSnooze = status?.slot?.let { ReminderSchedule.snoozeUntil(it, ZonedDateTime.now(container.clock)) } != null
                PaceCheckScreen(
                    status = status,
                    canSnooze = canSnooze,
                    onSnooze = { sendAction(ReminderReceiver.ACTION_SNOOZE) },
                    onPauseToday = { sendAction(ReminderReceiver.ACTION_PAUSE_TODAY) },
                    onDone = ::finish,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.visibleScreens.onScreenStarted()
    }

    override fun onStop() {
        container.visibleScreens.onScreenStopped()
        super.onStop()
    }

    private fun sendAction(action: String) {
        sendBroadcast(Intent(this, ReminderReceiver::class.java).setAction(action))
        finish()
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, ReminderActivity::class.java)
    }
}

@Composable
private fun PaceCheckScreen(
    status: DayStatus?,
    canSnooze: Boolean,
    onSnooze: () -> Unit,
    onPauseToday: () -> Unit,
    onDone: () -> Unit,
) {
    val content = status?.let(::paceCheckContent)
    LaunchedEffect(status) {
        if (status != null && content == null) onDone()
    }
    LaunchedEffect(Unit) {
        delay(SHOW_FOR_MS)
        onDone()
    }
    if (content == null) return

    val theme = LocalAppTheme.current
    val colors = MaterialTheme.colorScheme
    val todayColor = Color(if (content.goalMet) theme.goalMet else theme.today)

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(content.left, style = MaterialTheme.typography.displayMedium)
        Text(
            content.caption,
            style = MaterialTheme.typography.labelMedium,
            color = Color(theme.slot),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        ProgressBar(content.slotProgress, Color(theme.slot), Color(theme.track))
        Spacer(Modifier.height(6.dp))
        Text(
            content.todayLine,
            style = MaterialTheme.typography.labelSmall,
            color = todayColor,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        ProgressBar(content.todayProgress, todayColor, Color(theme.track), content.paceMarker)
        Spacer(Modifier.height(2.dp))
        Text(
            content.paceLine,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (canSnooze) {
                ReminderAction(R.drawable.ic_action_snooze, "Snooze 5 minutes", onSnooze)
            }
            ReminderAction(R.drawable.ic_action_pause, "Pause today", onPauseToday)
        }
    }
}

@Composable
private fun ReminderAction(icon: Int, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

/** A rounded bar with an optional tick marking pace. The tick is taller, so it reads over the fill. */
@Composable
private fun ProgressBar(progress: Float, color: Color, track: Color, marker: Float? = null) {
    val tickColor = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val barHeight = 8.dp.toPx()
        val top = (size.height - barHeight) / 2
        val radius = CornerRadius(barHeight / 2)
        drawRoundRect(track, Offset(0f, top), Size(size.width, barHeight), radius)
        if (progress > 0f) {
            val width = (size.width * progress).coerceAtLeast(barHeight)
            drawRoundRect(color, Offset(0f, top), Size(width, barHeight), radius)
        }
        marker?.let {
            val tickWidth = 2.dp.toPx()
            drawRect(tickColor, Offset(size.width * it - tickWidth / 2, 0f), Size(tickWidth, size.height))
        }
    }
}
