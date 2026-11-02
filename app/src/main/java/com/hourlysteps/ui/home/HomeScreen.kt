package com.hourlysteps.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.PagerState
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.drawCircularProgressIndicator
import com.hourlysteps.domain.DayStatus
import com.hourlysteps.domain.DayStatusKind
import com.hourlysteps.surfaces.ringContent
import com.hourlysteps.ui.LocalAppTheme

private const val PAGE_COUNT = 3

/** Swipeable pages: the rings, today slot by slot, then the streak. */
@Composable
fun HomeScreen(viewModel: HomeViewModel, onOpenSettings: () -> Unit) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { PAGE_COUNT }

    // The stock page indicator sits exactly where the Settings button is, so the rings page draws its own.
    HorizontalPagerScaffold(pagerState = pagerState, pageIndicator = null) {
        HorizontalPager(state = pagerState) { page ->
            AnimatedPage(pageIndex = page, pagerState = pagerState) {
                when (page) {
                    0 -> status?.let { HomeContent(it, pagerState, viewModel::resume, onOpenSettings) }
                    1 -> today?.let { TodayPage(it) }
                    else -> streak?.let { StreakPage(it) }
                }
            }
        }
    }
}

@Composable
fun HomeContent(status: DayStatus, pagerState: PagerState, onResume: () -> Unit, onOpenSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val content = ringContent(status)
    val theme = LocalAppTheme.current
    val todayColor = Color(if (content.goalMet) theme.goalMet else theme.today)
    val slotColor = Color(theme.slot)

    Box(modifier = Modifier.fillMaxSize()) {
        Ring(content.todayProgress, todayColor, Modifier.padding(4.dp))
        Ring(content.slotProgress, slotColor, Modifier.padding(4.dp + RingStroke + RingGap))

        Column(
            modifier = Modifier.fillMaxSize().padding(start = 30.dp, end = 30.dp, top = 28.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            content.title?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = todayColor, textAlign = TextAlign.Center)
            }
            Text(content.value, style = MaterialTheme.typography.displayMedium)
            content.valueCaption?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (content.captionIsSlot) slotColor else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            content.todayLine?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = todayColor, textAlign = TextAlign.Center)
            }
            content.paceLine?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
            content.detailLine?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            if (status.kind == DayStatusKind.PAUSED) {
                CompactButton(onClick = onResume, label = { Text("Resume") })
            }
        }

        PageDots(pagerState, Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp))

        EdgeButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.BottomCenter),
            buttonSize = EdgeButtonSize.ExtraSmall,
        ) {
            Text("Settings")
        }
    }
}

private val RingStroke = 8.dp
private val RingGap = 3.dp

/**
 * Drawn directly rather than with the animated CircularProgressIndicator composable: that one
 * keeps the progress it was first composed with and drops the next update, so after navigating
 * back it can show a stale value indefinitely.
 */
@Composable
private fun Ring(progress: Float, color: Color, modifier: Modifier) {
    val ringColors = ProgressIndicatorDefaults.colors(indicatorColor = color, trackColor = Color(LocalAppTheme.current.track))
    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawCircularProgressIndicator(
                    progress = progress,
                    colors = ringColors,
                    strokeWidth = RingStroke,
                    startAngle = 120f,
                    endAngle = 60f,
                )
            },
    )
}

@Composable
private fun PageDots(pagerState: PagerState, modifier: Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(PAGE_COUNT) { index ->
            val selected = pagerState.currentPage == index
            Box(
                Modifier
                    .size(5.dp)
                    .background(
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    ),
            )
        }
    }
}
