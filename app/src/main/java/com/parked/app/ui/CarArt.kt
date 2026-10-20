package com.parked.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * An original side-view hatchback, drawn in the car's colour. Ported from the
 * approved 0.7 mockup (viewBox 400 x 170). Shared by the intro and the Car tab.
 */

private fun path(d: String) = PathParser().parsePathString(d).toPath()

private object CarPaths {
    val body = path(
        "M26 112 L26 92 Q28 78 44 70 L82 50 Q90 46 104 45 L214 44 Q226 44 236 50 L286 76 Q340 80 362 88 " +
            "Q376 92 378 104 L378 116 Q378 120 372 120 L341 120 A36 36 0 0 0 269 120 L132 120 A36 36 0 0 0 60 120 " +
            "L34 120 Q26 120 26 112 Z"
    )
    val windowFront = path("M174 51 L210 50 Q220 50 227 56 L261 76 L174 76 Z")
    val windowRear = path("M98 76 L102 57 Q105 52 113 52 L166 51 L166 76 Z")
    val windowBack = path("M56 72 L88 55 L92 76 L52 76 Z")
    val glare = path("M108 55 L150 54 L120 74 L104 74 Z")
    val shoulder = path("M44 84 Q200 72 362 92")
    val doorRear = path("M170 78 L169 113")
    val doorFront = path("M252 80 Q255 98 252 113")
    val mirror = path("M250 70 L263 66 L267 74 L254 77 Z")
    val headlight = path("M352 89 Q368 91 373 99 L356 99 Z")
    val taillight = path("M27 88 L39 85 L39 98 L27 98 Z")
    val sill = path("M132 112 L270 112 L270 120 L132 120 Z")
}

private fun shade(c: Color, f: Float): Color =
    if (f >= 0) lerp(c, Color.White, f) else lerp(c, Color.Black, -f)

/** The car on its own, 400:170, in [color]. */
@Composable
fun CarIllustration(color: Color, modifier: Modifier = Modifier) {
    val top = remember(color) { shade(color, 0.22f) }
    val low = remember(color) { shade(color, -0.28f) }
    Canvas(modifier.aspectRatio(400f / 170f)) {
        val k = size.width / 400f
        scale(k, k, pivot = Offset.Zero) {
            // Ground shadow: a few stacked ellipses fake the blur.
            for (i in 0 until 5) {
                val grow = i * 4f
                drawOval(
                    Color(0xFF1C1917).copy(alpha = 0.05f),
                    topLeft = Offset(26f - grow, 144f - grow / 3f),
                    size = Size(352f + grow * 2, 14f + grow / 1.5f),
                )
            }
            drawCircle(Color(0xFF26221F), 36.5f, Offset(96f, 120f))
            drawCircle(Color(0xFF26221F), 36.5f, Offset(305f, 120f))
            drawPath(CarPaths.body, Brush.verticalGradient(0f to top, 0.55f to color, 1f to low, startY = 44f, endY = 120f))
            // A white or silver car would melt into the white studio: give it an edge.
            if (color.luminance() > 0.6f) drawPath(CarPaths.body, Color(0xFFB8B2AA), style = Stroke(1.5f))
            val glass = Brush.linearGradient(listOf(Color(0xFF2A333C), Color(0xFF46525E)), start = Offset(52f, 50f), end = Offset(261f, 76f))
            drawPath(CarPaths.windowRear, glass)
            drawPath(CarPaths.windowFront, glass)
            drawPath(CarPaths.windowBack, glass, alpha = 0.9f)
            drawPath(CarPaths.glare, Color.White, alpha = 0.10f)
            drawPath(CarPaths.shoulder, Color.White.copy(alpha = 0.28f), style = Stroke(2.5f))
            drawPath(CarPaths.doorRear, low.copy(alpha = 0.7f), style = Stroke(2f))
            drawPath(CarPaths.doorFront, low.copy(alpha = 0.7f), style = Stroke(2f))
            drawRoundRect(low, Offset(146f, 86f), Size(14f, 3.5f), CornerRadius(1.75f))
            drawRoundRect(low, Offset(228f, 87f), Size(14f, 3.5f), CornerRadius(1.75f))
            drawPath(CarPaths.mirror, low)
            drawPath(CarPaths.headlight, Color(0xFFFFF7E6))
            drawPath(CarPaths.taillight, Color(0xFFB91C1C))
            drawPath(CarPaths.sill, Color.Black, alpha = 0.16f)
            for (cx in floatArrayOf(96f, 305f)) {
                val c = Offset(cx, 120f)
                drawCircle(Color(0xFF1E1E1E), 30f, c)
                drawCircle(Color(0xFFD5D9DD), 21f, c)
                drawCircle(Color(0xFFC2C7CC), 18f, c)
                for (a in 0 until 5) {
                    rotate(a * 72f, pivot = c) {
                        drawRoundRect(Color(0xFF9AA1A8), Offset(cx - 2f, 102f), Size(4f, 15f), CornerRadius(2f))
                    }
                }
                drawCircle(Color(0xFF7D848B), 5f, c)
            }
        }
    }
}

/**
 * Rivian-style studio: a white backdrop with a soft warm floor, bordered card,
 * the car standing on the floor line. [content] draws on top (a label, say).
 */
@Composable
fun CarStudio(
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
    carWidth: Dp = 300.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RadiusCard)
            .background(
                Brush.verticalGradient(
                    0f to Color.White, 0.66f to Color.White,
                    0.66f to Color(0xFFF1ECE4), 1f to Color(0xFFFBF8F3),
                )
            )
            .border(1.dp, Border, RadiusCard),
    ) {
        CarIllustration(
            color,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .widthIn(max = carWidth)
                .fillMaxWidth(0.86f)
        )
        content()
    }
}
