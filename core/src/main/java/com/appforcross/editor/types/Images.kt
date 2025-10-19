package com.appforcross.editor.types

data class LinearImageF16(val width: Int, val height: Int, val planes: Int, val data: ShortArray)
data class U8Mask(val width: Int, val height: Int, val data: ByteArray)
data class ColorMeta(val iccSpace: String, val iccConfidence: Float, val hdrMode: HdrMode)
enum class HdrMode { HDR_OFF, HLG, PQ, GAINMAP }

sealed interface ImageSource {
    data class Bytes(val data: ByteArray) : ImageSource
    data class FilePath(val path: String) : ImageSource
}

/** Простая half<->float конверсия (без Android-зависимостей). */
object HalfFloats {
    fun fromFloat(f: Float): Short {
        val bits = java.lang.Float.floatToIntBits(f)
        val sign = (bits ushr 16) and 0x8000
        var exp = (bits ushr 23) and 0xFF
        var mant = bits and 0x7FFFFF
        if (exp == 255) { // Inf/NaN -> Inf
            return (sign or 0x7C00).toShort()
        }
        exp -= 112
        if (exp <= 0) {
            if (exp < -10) return sign.toShort()
            mant = (mant or 0x00800000) ushr (1 - exp)
            return (sign or (mant ushr 13)).toShort()
        }
        if (exp >= 31) {
            return (sign or 0x7C00).toShort()
        }
        return (sign or (exp shl 10) or (mant ushr 13)).toShort()
    }
    fun toFloat(h: Short): Float {
        val bits = h.toInt() and 0xFFFF
        val sign = (bits and 0x8000) shl 16
        var exp = (bits ushr 10) and 0x1F
        var mant = bits and 0x03FF
        if (exp == 0) {
            if (mant == 0) return java.lang.Float.intBitsToFloat(sign)
            while (mant and 0x0400 == 0) { mant = mant shl 1; exp -= 1 }
            exp += 1; mant = mant and 0x03FF
        } else if (exp == 31) {
            return java.lang.Float.intBitsToFloat(sign or 0x7F800000 or (mant shl 13))
        }
        exp = exp + (127 - 15)
        val fbits = sign or (exp shl 23) or (mant shl 13)
        return java.lang.Float.intBitsToFloat(fbits)
    }
}
