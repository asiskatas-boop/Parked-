package com.parked.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Front view of the car, drawn as vector shapes in the chosen colour. Replaces
 * the old 450×375 bitmaps, which were themselves enlarged from smaller images
 * and looked blocky at any size. Drawn on a 450×375 grid and scaled to fit.
 */
@Composable
fun CarArt(body: Color, description: String, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = description }) {
        val scale = minOf(size.width / W, size.height / H)
        val dx = (size.width - W * scale) / 2f
        val dy = (size.height - H * scale) / 2f
        withTransform({
            translate(dx, dy)
            scale(scale, scale, Offset.Zero)
        }) {
            drawCar(body)
        }
    }
}

private const val W = 450f
private const val H = 375f

private fun DrawScope.drawCar(body: Color) {
    val dark = lerp(body, Color.Black, 0.28f)
    val darker = lerp(body, Color.Black, 0.45f)
    val glass = lerp(lerp(body, Color(0xFF1B2430), 0.62f), Color.White, 0.08f)
    // Very light paint needs an edge to stand out on the white page.
    val edge = if (body.luminanceApprox() > 0.75f) Color(0xFFB9BEB6) else Color.Transparent
    val tyre = Color(0xFF26292C)

    // Tyres
    drawRoundRect(tyre, Offset(58f, 268f), Size(68f, 62f), CornerRadius(14f))
    drawRoundRect(tyre, Offset(324f, 268f), Size(68f, 62f), CornerRadius(14f))

    // Mirrors
    drawRoundRect(dark, Offset(26f, 112f), Size(58f, 34f), CornerRadius(17f))
    drawRoundRect(dark, Offset(366f, 112f), Size(58f, 34f), CornerRadius(17f))

    // Cabin (rounded trapezoid)
    val cabin = Path().apply {
        moveTo(112f, 40f)
        lineTo(338f, 40f)
        quadraticTo(356f, 40f, 362f, 58f)
        lineTo(388f, 150f)
        lineTo(62f, 150f)
        lineTo(88f, 58f)
        quadraticTo(94f, 40f, 112f, 40f)
        close()
    }
    drawPath(cabin, body)
    if (edge != Color.Transparent) drawPath(cabin, edge, style = Stroke(3f))

    // Windscreen with a soft reflection
    val glassPath = Path().apply {
        moveTo(124f, 60f)
        lineTo(326f, 60f)
        lineTo(348f, 138f)
        lineTo(102f, 138f)
        close()
    }
    drawPath(glassPath, glass)
    val shine = Path().apply {
        moveTo(250f, 60f)
        lineTo(296f, 60f)
        lineTo(232f, 138f)
        lineTo(186f, 138f)
        close()
    }
    drawPath(shine, Color.White.copy(alpha = 0.10f))

    // Body
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(body, body, dark), startY = 130f, endY = 300f),
        topLeft = Offset(44f, 128f),
        size = Size(362f, 170f),
        cornerRadius = CornerRadius(44f)
    )
    if (edge != Color.Transparent) {
        drawRoundRect(edge, Offset(44f, 128f), Size(362f, 170f), CornerRadius(44f), style = Stroke(3f))
    }
    // Shoulder highlight
    drawRoundRect(Color.White.copy(alpha = 0.14f), Offset(70f, 140f), Size(310f, 14f), CornerRadius(7f))

    // Headlights
    for (cx in listOf(104f, 346f)) {
        drawCircle(Color(0xFFFFF6C8), radius = 19f, center = Offset(cx, 196f))
        drawCircle(Color.White, radius = 10f, center = Offset(cx, 196f))
    }

    // Grille
    drawRoundRect(darker, Offset(156f, 226f), Size(138f, 30f), CornerRadius(15f))
    drawRoundRect(Color.White.copy(alpha = 0.12f), Offset(168f, 233f), Size(114f, 4f), CornerRadius(2f))
}

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
