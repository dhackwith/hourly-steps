package com.hourlysteps.celebration

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.hourlysteps.HourlyStepsApp
import com.hourlysteps.domain.Celebration
import com.hourlysteps.domain.CelebrationKind
import com.hourlysteps.domain.Settings
import com.hourlysteps.domain.SlotWording
import com.hourlysteps.domain.Streaks
import com.hourlysteps.reminder.Haptics
import com.hourlysteps.ui.HourlyStepsTheme
import com.hourlysteps.ui.LocalAppTheme
import com.hourlysteps.ui.formatSteps
import kotlinx.coroutines.delay

/**
 * Full-screen "goal reached" moment: fireworks, a headline and the step count. Closes itself
 * after a few seconds, on tap, or on swipe.
 */
class CelebrationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Launched by a full-screen intent while the screen is off: wake it and show over the lock.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        getSystemService(NotificationManager::class.java).cancel(CelebrationManager.NOTIFICATION_ID)
        val container = (application as HourlyStepsApp).container
        container.celebrationManager.onScreenOpened()
        // Android drops a vibration asked for from the background, so the open screen plays it.
        if (savedInstanceState == null) Haptics.vibrate(this, intent.getLongArrayExtra(EXTRA_VIBRATION))

        val celebration = celebrationFrom(intent)
        val reduceMotion = AndroidSettings.Global.getFloat(
            contentResolver,
            AndroidSettings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f

        setContent {
            HourlyStepsTheme(container) {
                CelebrationScreen(celebration, reduceMotion, onDone = ::finish)
            }
        }
    }

    companion object {
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_STEPS = "steps"
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_TOTAL = "total"
        private const val EXTRA_DAILY_GOAL = "daily_goal"
        private const val EXTRA_STREAK = "streak"
        private const val EXTRA_SLOT_MINUTES = "slot_minutes"
        private const val EXTRA_VIBRATION = "vibration"

        fun intent(context: Context, c: Celebration, vibration: LongArray? = null): Intent =
            Intent(context, CelebrationActivity::class.java)
                .putExtra(EXTRA_VIBRATION, vibration)
                .putExtra(EXTRA_KIND, c.kind.name)
                .putExtra(EXTRA_STEPS, c.stepsInSlot)
                .putExtra(EXTRA_TARGET, c.target)
                .putExtra(EXTRA_TOTAL, c.todayTotal)
                .putExtra(EXTRA_DAILY_GOAL, c.dailyGoal)
                .putExtra(EXTRA_STREAK, c.streak)
                .putExtra(EXTRA_SLOT_MINUTES, c.slotMinutes)

        private fun celebrationFrom(intent: Intent) = Celebration(
            kind = intent.getStringExtra(EXTRA_KIND)
                ?.let { runCatching { CelebrationKind.valueOf(it) }.getOrNull() }
                ?: CelebrationKind.SLOT_GOAL,
            stepsInSlot = intent.getIntExtra(EXTRA_STEPS, 0),
            target = intent.getIntExtra(EXTRA_TARGET, 0),
            todayTotal = intent.getIntExtra(EXTRA_TOTAL, 0),
            dailyGoal = intent.getIntExtra(EXTRA_DAILY_GOAL, 0),
            streak = intent.getIntExtra(EXTRA_STREAK, 0),
            slotMinutes = intent.getIntExtra(EXTRA_SLOT_MINUTES, Settings.DEFAULT_SLOT_MINUTES),
        )
    }
}

private const val SHOW_FOR_MS = 7_000L
private const val SHOW_DAILY_FOR_MS = 10_000L

@Composable
fun CelebrationScreen(celebration: Celebration, reduceMotion: Boolean, onDone: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val isDaily = celebration.kind == CelebrationKind.DAILY_GOAL
    val theme = LocalAppTheme.current
    val gold = Color(theme.goalMet)
    val scale = remember { Animatable(if (reduceMotion) 1f else 0.4f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    LaunchedEffect(Unit) {
        delay(if (isDaily) SHOW_DAILY_FOR_MS else SHOW_FOR_MS)
        onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        Fireworks(
            colors = if (isDaily) {
                GoldFireworkColors
            } else {
                listOf(theme.today, theme.slot, theme.goalMet, theme.onPrimaryContainer, 0xFFFFFFFF).map { Color(it) }
            },
            modifier = Modifier.fillMaxSize(),
            animate = !reduceMotion,
            style = if (isDaily) FireworkStyle.GOLD else FireworkStyle.STANDARD,
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val (headline1, headline2) = when (celebration.kind) {
                CelebrationKind.SLOT_GOAL -> SlotWording.goalName(celebration.slotMinutes) to "achieved!"
                CelebrationKind.INACTIVITY_GOAL -> "Still moving" to "nice work!"
                CelebrationKind.DAILY_GOAL -> "Daily goal" to "achieved!"
            }
            Text(if (isDaily) "🏆" else "🎉", fontSize = 30.sp)
            Text(headline1, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                headline2,
                style = MaterialTheme.typography.titleLarge,
                color = if (isDaily) gold else colors.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                formatSteps(if (isDaily) celebration.todayTotal else celebration.stepsInSlot),
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                if (isDaily) "steps today" else "steps ${SlotWording.current(celebration.slotMinutes)}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            if (isDaily) {
                Streaks.line(celebration.streak)?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = gold, textAlign = TextAlign.Center)
                }
            } else if (celebration.dailyGoal > 0) {
                Text(
                    "Today ${formatSteps(celebration.todayTotal)} / ${formatSteps(celebration.dailyGoal)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
