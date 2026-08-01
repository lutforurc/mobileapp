package com.example.cashbookbd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cashbookbd.ui.theme.AppFontWeight
import com.example.cashbookbd.ui.theme.AppShape
import com.example.cashbookbd.ui.theme.appColors
import com.example.cashbookbd.ui.theme.asTint

/**
 * The step indicator for every multi-step form: where you are, how far along,
 * and a way to jump.
 *
 * The steps sit in a row that scrolls sideways. Dividing a phone's width between
 * six of them was what used to wrap every label onto two lines — a scrolling row
 * never divides it, so each keeps its own width and reads on one line however
 * many there are. The current step is scrolled back into view whenever it
 * changes, so Next never leaves it off the edge.
 */
@Composable
fun AppStepBar(
    steps: List<String>,
    currentStep: Int,
    onStepClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (steps.isEmpty()) return
    val listState = rememberLazyListState()

    // Centred rather than merely made visible: on the last steps the row would
    // otherwise stop with the current chip against the right edge, reading as
    // though there were nothing after it.
    LaunchedEffect(currentStep) {
        if (currentStep in steps.indices) {
            listState.animateScrollToItem(index = currentStep, scrollOffset = -StepScrollLead)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(steps) { index, step ->
                StepChip(
                    index = index,
                    label = step,
                    currentStep = currentStep,
                    onClick = { onStepClick(index) },
                )
            }
        }

        StepProgress(currentStep = currentStep, total = steps.size)
    }
}

/** One step in the row: its number, ticked once it is behind you, and its name. */
@Composable
private fun StepChip(
    index: Int,
    label: String,
    currentStep: Int,
    onClick: () -> Unit,
) {
    val isCurrent = index == currentStep
    val action = MaterialTheme.appColors.action

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(AppShape)
            .background(if (isCurrent) action.asTint() else MaterialTheme.appColors.cardMuted)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        StepMarker(index = index, currentStep = currentStep)
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isCurrent) AppFontWeight.Bold else AppFontWeight.Normal,
            color = if (isCurrent) action else MaterialTheme.appColors.textMuted,
            // The row scrolls, so a name has all the width it needs; one line is
            // a guard against an unusually long title, not the usual case.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A step's number — ticked once it is behind you. */
@Composable
private fun StepMarker(index: Int, currentStep: Int) {
    val isCurrent = index == currentStep
    val isDone = index < currentStep
    val action = MaterialTheme.appColors.action
    Box(
        modifier = Modifier
            .size(StepMarkerSize)
            .background(
                color = when {
                    isCurrent -> action
                    isDone -> action.asTint()
                    else -> MaterialTheme.appColors.card
                },
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isDone) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = action,
                modifier = Modifier.size(12.dp),
            )
        } else {
            Text(
                text = (index + 1).toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = AppFontWeight.SemiBold,
                color = if (isCurrent) {
                    MaterialTheme.appColors.onAction
                } else {
                    MaterialTheme.appColors.textMuted
                },
            )
        }
    }
}

/** How far through the form the current step is, as a filled fraction. */
@Composable
private fun StepProgress(currentStep: Int, total: Int) {
    if (total <= 0) return
    val done = (currentStep + 1).coerceIn(0, total).toFloat() / total
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(ProgressHeight)
            .clip(AppShape)
            .background(MaterialTheme.appColors.cardMuted),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(done)
                .height(ProgressHeight)
                .background(MaterialTheme.appColors.action),
        )
    }
}

private val StepMarkerSize = 20.dp
private val ProgressHeight = 3.dp

/** How much of the previous chip to leave showing, so the row reads as continuing. */
private const val StepScrollLead = 48
