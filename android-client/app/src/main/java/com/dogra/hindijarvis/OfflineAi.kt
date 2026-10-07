package com.dogra.hindijarvis

fun interface TokenCallback { fun onText(bytes: ByteArray) }

class OfflineAi {
    external fun generate(path: ByteArray, prompt: ByteArray, callback: TokenCallback): ByteArray
    external fun setCancelled(cancelled: Boolean)
    external fun close()
    companion object { init { System.loadLibrary("jarvis_offline") } }
}
