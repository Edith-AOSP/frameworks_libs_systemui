package com.android.launcher3.icons

import android.graphics.drawable.AdaptiveIconDrawable

/**
 * Adaptive icon supplied by an icon pack.
 *
 * The layers are kept so the launcher can draw them full-bleed and mask them with the selected
 * icon shape, instead of flattening the pack art (which would bake in the system mask).
 */
class IconPackAdaptiveDrawable(base: AdaptiveIconDrawable) :
    AdaptiveIconDrawable(base.background, base.foreground, base.monochrome), IconPackDrawable {

    override val isFromIconPack: Boolean get() = true
    override val isPreShaped: Boolean get() = false
}
