package org.succlz123.memorycache.lib.trimmable

enum class MemoryTrimType(val suggestedTrimRatio: Double) {

    OnCloseToDalvikHeapLimit(0.5),

    OnSystemLowMemoryWhileAppInForeground(0.5),

    OnSystemLowMemoryWhileAppInBackground(1.0),

    OnAppBackgrounded(1.0)
}

interface MemoryTrimmable {

    fun trim(trimType: MemoryTrimType)
}
