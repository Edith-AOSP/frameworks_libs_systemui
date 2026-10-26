package com.android.launcher3.icons

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/** Marker for drawables supplied by an icon pack. */
interface IconPackDrawable {
    val isFromIconPack: Boolean

    /**
     * True when the pack art already carries its own shape (legacy bitmaps), so it must not be
     * re-masked with the launcher's icon shape. False for adaptive/vector pack art.
     */
    val isPreShaped: Boolean
}

open class ExtendedBitmapDrawable(
    res: Resources,
    bitmap: Bitmap,
    override val isFromIconPack: Boolean,
) : BitmapDrawable(res, bitmap), IconPackDrawable {

    override val isPreShaped: Boolean get() = true

    companion object {

        /** Wrap a pack drawable, flattening only art that has no adaptive layers to mask. */
        @JvmStatic
        fun wrap(res: Resources, drawable: Drawable?, isFromIconPack: Boolean): Drawable? {
            if (drawable == null) return null
            if (drawable is BitmapDrawable) {
                return ExtendedBitmapDrawable(res, drawable.bitmap, isFromIconPack)
            }
            if (!isFromIconPack) return drawable
            val width = drawable.intrinsicWidth.coerceAtLeast(1)
            val height = drawable.intrinsicHeight.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            return ExtendedBitmapDrawable(res, bitmap, true)
        }

        @JvmStatic
        val Drawable.isFromIconPack get() = (this as? IconPackDrawable)?.isFromIconPack ?: false

        @JvmStatic
        val Drawable.isPreShapedIconPack get() = (this as? IconPackDrawable)?.isPreShaped ?: false
    }
}
