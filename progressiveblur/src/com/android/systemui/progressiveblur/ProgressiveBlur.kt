/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.progressiveblur

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A reusable **progressive blur** modifier: like `Modifier.blur`, but the blur radius varies across
 * the surface along a linear gradient, so it can ramp from sharp at one edge to [radius] at the
 * other (e.g. a frosted bottom edge).
 *
 * Implemented with only API-33 primitives (`android.graphics.RuntimeShader` +
 * `RenderEffect.createRuntimeShaderEffect`) executed through `Modifier.graphicsLayer`, so it does
 * **not** depend on newer Compose versions that ship a native progressive-blur API. On API < 33 the
 * effect is a no-op (content is drawn unblurred).
 *
 * This library is intentionally self-contained so it can be reused from any repo by depending on the
 * `ProgressiveBlurComposeLib` Soong module.
 *
 * @param radius maximum blur radius, reached at the [startPoint] end of the ramp.
 * @param startPoint ramp start in the layer's pixel coordinates; blur is strongest here.
 * @param endPoint ramp end; blur falls to zero here.
 * @param startIntensity intensity multiplier at [startPoint] (0..1).
 * @param endIntensity intensity multiplier at [endPoint] (0..1).
 */
@Stable
fun Modifier.progressiveBlur(
    radius: Dp,
    startPoint: Offset,
    endPoint: Offset,
    startIntensity: Float = 1f,
    endIntensity: Float = 0f,
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    return this.graphicsLayer {
        renderEffect =
            progressiveBlurRenderEffect(
                radiusPx = radius.toPx(),
                sizeWidth = size.width,
                sizeHeight = size.height,
                start = startPoint,
                end = endPoint,
                startIntensity = startIntensity,
                endIntensity = endIntensity,
            )
                .asComposeRenderEffect()
    }
}

/**
 * Convenience for the common case: a vertical fade-to-blur anchored at the **bottom edge** of the
 * content, ramping from zero blur [bandHeight] above the bottom to full [radius] at the bottom.
 *
 * @param radius maximum blur radius at the bottom edge.
 * @param bandHeight height of the transition band, measured up from the bottom edge.
 */
@Stable
fun Modifier.bottomProgressiveBlur(radius: Dp, bandHeight: Dp): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    return this.graphicsLayer {
        val x = size.width / 2f
        renderEffect =
            progressiveBlurRenderEffect(
                radiusPx = radius.toPx(),
                sizeWidth = size.width,
                sizeHeight = size.height,
                start = Offset(x, size.height),
                end = Offset(x, (size.height - bandHeight.toPx()).coerceAtLeast(0f)),
                startIntensity = 1f,
                endIntensity = 0f,
            )
                .asComposeRenderEffect()
    }
}

/** Sensible default maximum blur radius for a frosted edge. */
val DefaultProgressiveBlurRadius: Dp = 24.dp

/**
 * Builds a [RenderEffect] that blurs [sizeWidth]x[sizeHeight] content with a radius that ramps
 * linearly from `start` to `end`.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun progressiveBlurRenderEffect(
    radiusPx: Float,
    sizeWidth: Float,
    sizeHeight: Float,
    start: Offset,
    end: Offset,
    startIntensity: Float,
    endIntensity: Float,
): RenderEffect {
    val shader = RuntimeShader(progressiveLinearBlurSkSl(isVerticalBlur = true))
    shader.setFloatUniform("blurRadius", radiusPx)
    // crop = (left, top, right, bottom) used by the shader's in-bounds test.
    shader.setFloatUniform("crop", 0f, 0f, sizeWidth, sizeHeight)
    // Clamp (0) rather than decal (1): renormalize interior samples so the edge doesn't fade out.
    shader.setFloatUniform("unbounded", 0f)
    shader.setFloatUniform("startIntensity", startIntensity)
    shader.setFloatUniform("endIntensity", endIntensity)
    shader.setFloatUniform("startPoint", start.x, start.y)
    shader.setFloatUniform("endPoint", end.x, end.y)
    return RenderEffect.createRuntimeShaderEffect(shader, "content")
}

/**
 * SkSL for a separable gaussian blur whose radius is modulated by a linear gradient between
 * `startPoint` and `endPoint` (in layer pixels). Faithful port of the vertical variant of AOSP
 * `androidx.compose.ui.graphics.blur.BlurShaders.progressiveLinearBlurSkSl`.
 */
internal fun progressiveLinearBlurSkSl(isVerticalBlur: Boolean): String {
    val loopOffset =
        if (isVerticalBlur) "vec2(0.0, i + weightH / weight)" else "vec2(i + weightH / weight, 0.0)"
    val oddOffset = if (isVerticalBlur) "vec2(0.0, r)" else "vec2(r, 0.0)"
    val boundsBody =
        if (isVerticalBlur) {
            "return step(bounds.y, sampleCoord.y) * (1.0 - step(bounds.w, sampleCoord.y));"
        } else {
            "return step(bounds.x, sampleCoord.x) * (1.0 - step(bounds.z, sampleCoord.x));"
        }

    val header =
        """
        uniform shader content;
        uniform float blurRadius;
        uniform float4 crop;
        uniform float unbounded;
        const float maxRadius = $MAX_BLUR_RADIUS_PX;
        uniform float startIntensity;
        uniform float endIntensity;
        uniform float2 startPoint;
        uniform float2 endPoint;
        """
            .trimIndent()

    val main =
        """
        half4 main(float2 coord) {
            float2 pa = coord - startPoint;
            float2 ba = endPoint - startPoint;
            float fraction = clamp(dot(pa, ba) / max(dot(ba, ba), 0.0001), 0.0, 1.0);
            float intensity = mix(startIntensity, endIntensity, fraction);
            float radius = blurRadius * intensity;
            return half4(blur(coord, radius));
        }
        """
            .trimIndent()

    return header + "\n\n" + blurCoreSkSl(loopOffset, oddOffset, boundsBody) + "\n\n" + main
}

private fun blurCoreSkSl(loopOffset: String, oddOffset: String, boundsBody: String): String =
    """
    float gaussian(float x, float sigma) {
        return exp(-(x * x) / (2.0 * sigma * sigma));
    }

    float inBoundsOnMovedAxis(vec2 sampleCoord, float4 bounds) {
        $boundsBody
    }

    vec4 blur(vec2 coord, float radius) {
        float r = floor(radius);
        if (r < 1.0) { return content.eval(coord); }

        float sigma = max(radius / 2.0, 1.0);
        float weightSum = 1.0;
        vec4 result = content.eval(coord);

        for (float i = 1.0; i < maxRadius; i += 2.0) {
            if (i >= r) { break; }
            float weightL = gaussian(i, sigma);
            float weightH = gaussian(i + 1.0, sigma);
            float weight = weightL + weightH;
            vec2 offset = $loopOffset;

            vec2 newCoord1 = coord - offset;
            float mask1 = inBoundsOnMovedAxis(newCoord1, crop);
            weightSum += weight * max(mask1, unbounded);
            if (mask1 > 0.0) { result += weight * content.eval(newCoord1); }

            vec2 newCoord2 = coord + offset;
            float mask2 = inBoundsOnMovedAxis(newCoord2, crop);
            weightSum += weight * max(mask2, unbounded);
            if (mask2 > 0.0) { result += weight * content.eval(newCoord2); }
        }

        float oddMask = mod(r, 2.0) * (1.0 - step(maxRadius, r));
        float oddWeight = gaussian(r, sigma) * oddMask;
        vec2 oddOffset = $oddOffset;

        vec2 oddCoord1 = coord - oddOffset;
        float oddBounds1 = inBoundsOnMovedAxis(oddCoord1, crop);
        weightSum += oddWeight * max(oddBounds1, unbounded);
        if (oddBounds1 > 0.0) { result += oddWeight * content.eval(oddCoord1); }

        vec2 oddCoord2 = coord + oddOffset;
        float oddBounds2 = inBoundsOnMovedAxis(oddCoord2, crop);
        weightSum += oddWeight * max(oddBounds2, unbounded);
        if (oddBounds2 > 0.0) { result += oddWeight * content.eval(oddCoord2); }

        return result / weightSum;
    }
    """
        .trimIndent()

private const val MAX_BLUR_RADIUS_PX = 150f
