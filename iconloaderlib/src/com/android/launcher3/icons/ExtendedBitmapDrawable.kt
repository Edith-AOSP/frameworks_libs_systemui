package com.android.launcher3.icons

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

open class ExtendedBitmapDrawable(
    res: Resources,
    bitmap: Bitmap,
    val isFromIconPack: Boolean
) : BitmapDrawable(res, bitmap) {

    companion object {

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
        val Drawable.isFromIconPack get() = (this as? ExtendedBitmapDrawable)?.isFromIconPack ?: false
    }
}
