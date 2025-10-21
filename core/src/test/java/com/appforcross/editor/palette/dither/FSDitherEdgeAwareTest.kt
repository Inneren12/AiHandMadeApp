+package com.appforcross.editor.palette.dither
+
+import kotlin.test.Test
+import kotlin.test.assertTrue
+
+class FSDitherEdgeAwareTest {
+
+    @Test
+    fun diffusionAlignsWithHorizontalStripes() {
+        val W = 24
+        val H = 24
+        val rgb = FloatArray(W * H * 3)
+        var p = 0
+        for (y in 0 until H) {
+            val stripe = if ((y / 4) % 2 == 0) 0.38f else 0.62f
+            for (x in 0 until W) {
+                val jitter = if (x % 2 == 0) 0.015f else -0.015f
+                val v = (stripe + jitter).coerceIn(0f, 1f)
+                rgb[p++] = v
+                rgb[p++] = v
+                rgb[p++] = v
+            }
+        }
+        val palette = floatArrayOf(
+            0.30f, 0.30f, 0.30f,
+            0.70f, 0.70f, 0.70f
+        )
+        val assign = IntArray(W * H) { 0 }
+
+        val params = DitherParams(mode = DitherParams.Mode.FS)
+        val index = Dither.apply(rgb, assign, palette, W, H, params).index
+
+        var horizontalTransitions = 0
+        var verticalTransitions = 0
+        for (y in 0 until H) {
+            for (x in 0 until W - 1) {
+                val idx = y * W + x
+                if (index[idx] != index[idx + 1]) horizontalTransitions++
+            }
+        }
+        for (y in 0 until H - 1) {
+            for (x in 0 until W) {
+                val idx = y * W + x
+                if (index[idx] != index[idx + W]) verticalTransitions++
+            }
+        }
+
+        assertTrue(horizontalTransitions > 0, "Dither should create horizontal mixing")
+        assertTrue(
+            verticalTransitions * 3 < horizontalTransitions,
+            "Orientation-aware diffusion must suppress vertical bleeding"
+        )
+    }
+}
+
