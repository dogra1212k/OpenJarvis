package com.dogra.hindijarvis

import android.graphics.Bitmap

class LogoRenderer {
    external fun renderLogo(bitmap: Bitmap)
    fun bitmap(): Bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).also { renderLogo(it) }
    companion object { init { System.loadLibrary("jarvis_offline") } }
}
