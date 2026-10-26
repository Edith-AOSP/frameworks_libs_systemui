package com.android.launcher3.icons

import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper

/**
 * Shapeless icon-pack art (vector / layer-list): the launcher draws it full-bleed and masks it
 * with the selected icon shape, like a normal adaptive icon.
 */
class IconPackShapelessDrawable(drawable: Drawable) :
    DrawableWrapper(drawable), IconPackDrawable {

    override val isFromIconPack: Boolean get() = true
    override val isPreShaped: Boolean get() = false
}
