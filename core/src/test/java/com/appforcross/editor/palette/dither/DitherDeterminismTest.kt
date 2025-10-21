+package com.appforcross.editor.palette.dither
+
+import kotlin.test.Test
+import kotlin.test.assertContentEquals
+import kotlin.test.assertFalse
+
+class DitherDeterminismTest {
+
+    @Test
+    fun orderedModeStableForSameSeed() {
+        val W = 48
+        val H = 24
+        val rgb = FloatArray(W * H * 3)
+        var p = 0
+        for (y in 0 until H) {
+            for (x in 0 until W) {
+                val v = (x.toFloat() / (W - 1).toFloat()).coerceIn(0f, 1f)
+                rgb[p++] = v
+                rgb[p++] = v * 0.9f
+                rgb[p++] = (1f - v * 0.8f)
+            }
+        }
+        val palette = FloatArray(5 * 3)
+        for (k in 0 until 5) {
+            val t = k / 4f
+            palette[k * 3] = t
+            palette[k * 3 + 1] = t * 0.8f
+            palette[k * 3 + 2] = (1f - t * 0.6f)
+        }
+        val assign = IntArray(W * H) { (it % 5) }
+
+        val params = DitherParams(mode = DitherParams.Mode.ORDERED, seed = 0xBEEFCAFEL)
+        val first = Dither.apply(rgb, assign, palette, W, H, params)
+        val second = Dither.apply(rgb, assign, palette, W, H, params)
+
+        assertContentEquals(first.index, second.index, "Ordered dither must be deterministic for same seed")
+
+        val changedSeed = params.copy(seed = 0xABCD1234L)
+        val different = Dither.apply(rgb, assign, palette, W, H, changedSeed)
+        assertFalse(first.index.contentEquals(different.index), "Changing seed should alter ordered pattern")
+    }
+
+    @Test
+    fun fsModeDeterministicAcrossRuns() {
+        val W = 32
+        val H = 16
+        val rgb = FloatArray(W * H * 3)
+        var p = 0
+        for (y in 0 until H) {
+            for (x in 0 until W) {
+                val v = ((y + x).toFloat() / (W + H).toFloat()).coerceIn(0f, 1f)
+                rgb[p++] = v
+                rgb[p++] = v
+                rgb[p++] = v
+            }
+        }
+        val palette = floatArrayOf(
+            0.0f, 0.0f, 0.0f,
+            0.5f, 0.5f, 0.5f,
+            1.0f, 1.0f, 1.0f
+        )
+        val assign = IntArray(W * H) { 1 }
+
+        val params = DitherParams(mode = DitherParams.Mode.FS)
+        val first = Dither.apply(rgb, assign, palette, W, H, params)
+        val second = Dither.apply(rgb, assign, palette, W, H, params)
+
+        assertContentEquals(first.index, second.index, "FS traversal must be deterministic")
+    }
+}
+
