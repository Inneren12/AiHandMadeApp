package com.appforcross.editor.types

/** Represents a linear floating-point image stored as IEEE754 half floats. */
data class LinearImageF16(val width: Int, val height: Int, val planes: Int, val data: ShortArray)

/** Represents a binary or grayscale mask with unsigned byte storage. */
data class U8Mask(val width: Int, val height: Int, val data: ByteArray)

/** Metadata describing color profile and HDR mode of an image. */
data class ColorMeta(val iccSpace: String, val iccConfidence: Float, val hdrMode: HdrMode)

/** Enumerates HDR processing modes supported by the pipeline. */
enum class HdrMode { HDR_OFF, HLG, PQ, GAINMAP }

/** Sealed representation of an image source. */
sealed interface ImageSource {
    data class Bytes(val data: ByteArray) : ImageSource
    data class FilePath(val path: String) : ImageSource
}

/** Half precision conversion helpers for deterministic storage. */
object HalfFloats {
    /** Converts IEEE754 half precision bits into Float. */
    @Suppress("MagicNumber")
    fun toFloat(h: Short): Float {
        val bits = h.toInt() and 0xFFFF
        val sign = (bits and 0x8000) shl 16
        var exponent = (bits ushr 10) and 0x1F
        var mantissa = bits and 0x03FF
        if (exponent == 0) {
            if (mantissa == 0) {
                return Float.fromBits(sign)
            }
            while ((mantissa and 0x0400) == 0) {
                mantissa = mantissa shl 1
                exponent -= 1
            }
            exponent += 1
            mantissa = mantissa and 0x03FF
        } else if (exponent == 0x1F) {
            val infNan = sign or 0x7F800000 or (mantissa shl 13)
            return Float.fromBits(infNan)
        }
        exponent = exponent + (127 - 15)
        val value = sign or (exponent shl 23) or (mantissa shl 13)
        return Float.fromBits(value)
    }

    /** Converts a Float into IEEE754 half precision bits with round-to-nearest-even. */
    @Suppress("MagicNumber")
    fun fromFloat(f: Float): Short {
        val bits = java.lang.Float.floatToIntBits(f)
        val sign = (bits ushr 16) and 0x8000
        var exponent = (bits ushr 23) and 0xFF
        var mantissa = bits and 0x7FFFFF
        if (exponent == 0xFF) {
            return (sign or 0x7C00 or (mantissa.takeIf { it != 0 }?.let { 0x1 } ?: 0)).toShort()
        }
        exponent = exponent - 127 + 15
        if (exponent >= 0x1F) {
            return (sign or 0x7C00).toShort()
        }
        if (exponent <= 0) {
            if (exponent < -10) {
                return sign.toShort()
            }
            mantissa = mantissa or 0x00800000
            val shift = 1 - exponent
            val rounded = (mantissa shr (shift + 12)) + ((mantissa shr (shift + 11)) and 1)
            return (sign or rounded).toShort()
        }
        val rounded = ((mantissa + 0x00001000) shr 13) and 0x03FF
        return (sign or (exponent shl 10) or rounded).toShort()
    }
}
