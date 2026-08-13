package com.example.archerytimer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.nativeCanvas

@Composable
fun FlipNumberDisplay(value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        value.forEachIndexed { index, digit ->
            FlipDigitCard(
                digit = digit,
                modifier = Modifier,
                animationKey = index,
            )
        }
    }
}

@Composable
private fun FlipDigitCard(
    digit: Char,
    modifier: Modifier = Modifier,
    animationKey: Int,
) {
    val progress = remember(animationKey) { Animatable(1f) }
    var displayedDigit by remember(animationKey) { mutableStateOf(digit) }
    var previousDigit by remember(animationKey) { mutableStateOf(digit) }

    LaunchedEffect(digit) {
        if (digit == displayedDigit) return@LaunchedEffect
        previousDigit = displayedDigit
        displayedDigit = digit
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 650, easing = LinearEasing))
        previousDigit = displayedDigit
    }

    val cardWidth = 76.dp
    val cardHeight = 108.dp
    val fraction = progress.value

    Box(
        modifier = modifier.width(cardWidth).height(cardHeight),
        contentAlignment = Alignment.Center,
    ) {
        DigitHalf(displayedDigit, top = true, cardWidth, cardHeight)
        DigitHalf(previousDigit, top = false, cardWidth, cardHeight)

        if (fraction < 0.5f) {
            DigitHalf(
                digit = previousDigit,
                top = true,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                modifier = Modifier.graphicsLayer {
                    rotationX = -180f * fraction
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    cameraDistance = 16f * density
                },
            )
        } else {
            DigitHalf(
                digit = displayedDigit,
                top = false,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                modifier = Modifier.graphicsLayer {
                    rotationX = 180f * (1f - fraction)
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    cameraDistance = 16f * density
                },
            )
        }

        Box(
            Modifier
                .width(cardWidth)
                .height(1.dp)
                .background(Color(0xFFBDBDBD))
                .align(Alignment.Center),
        )
    }
}

@Composable
private fun BoxScope.DigitHalf(
    digit: Char,
    top: Boolean,
    cardWidth: Dp,
    cardHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val halfHeight = cardHeight / 2
    Box(
        modifier = modifier
            .width(cardWidth)
            .height(halfHeight)
            .align(if (top) Alignment.TopCenter else Alignment.BottomCenter)
            .clip(
                if (top) RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                else RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
            )
            .background(if (top) Color(0xFFFFFFFF) else Color(0xFFF5F5F5)),
    ) {
        Canvas(modifier = Modifier.width(cardWidth).height(halfHeight)) {
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = 76.dp.toPx()
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD,
                )
            }
            val metrics = paint.fontMetrics
            val fullCardBaseline = cardHeight.toPx() / 2f -
                (metrics.ascent + metrics.descent) / 2f
            val baselineInHalf = if (top) fullCardBaseline else fullCardBaseline - halfHeight.toPx()

            drawContext.canvas.nativeCanvas.drawText(
                digit.toString(),
                size.width / 2f,
                baselineInHalf,
                paint,
            )
        }
    }
}
