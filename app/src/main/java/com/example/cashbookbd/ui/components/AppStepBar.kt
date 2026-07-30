package com.example.cashbookbd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Only the current step is named. Six steps divided across a phone's width left
 * every label wrapping onto two lines, and the row of stacked numbers and
 * half-words was the clutter — so the other steps move into a menu behind the
 * title, which is also what makes them tappable without competing for space.
 * Someone editing one setting still jumps straight to it, as the web allows.
 */
@Composable
fun AppStepBar(
    steps: List<String>,
    currentStep: Int,
    onStepClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (steps.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val title = steps.getOrNull(currentStep).orEmpty()

    Column(modifier = modifier.fillMaxWidth()) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
            ) {
                Text(
                    text = "STEP ${currentStep + 1} OF ${steps.size}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = AppFontWeight.SemiBold,
                    color = MaterialTheme.appColors.textOnScreenMuted,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = AppFontWeight.Bold,
                        color = MaterialTheme.appColors.action,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Go to a step",
                        tint = MaterialTheme.appColors.action,
                    )
                }
            }

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                steps.forEachIndexed { index, step ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = step,
                                fontWeight = if (index == currentStep) {
                                    AppFontWeight.SemiBold
                                } else {
                                    AppFontWeight.Normal
                                },
                            )
                        },
                        leadingIcon = { StepMarker(index = index, currentStep = currentStep) },
                        onClick = {
                            expanded = false
                            onStepClick(index)
                        },
                    )
                }
            }
        }

        StepProgress(currentStep = currentStep, total = steps.size)
    }
}

/** A step's number in the menu — ticked once it is behind you. */
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
                    else -> MaterialTheme.appColors.cardMuted
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
