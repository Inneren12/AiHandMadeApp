+package com.appforcross.editor.palette.dither
+
+import kotlin.test.Test
+import kotlin.test.assertTrue
+
+class OrderedDitherMaskAmpTest {
+
+    @Test
+    fun highVarianceRegionReceivesMoreAlternateIndices() {
+        val W = 64
+        val H = 64
+        val rgb = FloatArray(W * H * 3)
+        var p = 0
+        for (y in 0 until H) {
+            for (x in 0 until W) {
+                val base = 0.26f
+                val value = if (x < W / 2) {
+                    base
+                } else {
+                    val jitter = if ((x + y) and 1 == 0) 0.02f else -0.02f
+                    (base + jitter).coerceIn(0f, 1f)
+                }
+                rgb[p++] = value
+                rgb[p++] = value
+                rgb[p++] = value
+            }
+        }
+        val palette = floatArrayOf(
+            0.20f, 0.20f, 0.20f,
+            0.36f, 0.36f, 0.36f
+        )
+        val assign = IntArray(W * H) { 0 }
+
+        val params = DitherParams(mode = DitherParams.Mode.ORDERED)
+        val result = Dither.apply(rgb, assign, palette, W, H, params)
+        val index = result.index
+
+        var leftAlt = 0
+        var rightAlt = 0
+        for (y in 0 until H) {
+            for (x in 0 until W) {
+                val idx = y * W + x
+                if (index[idx] == 1) {
+                    if (x < W / 2) leftAlt++ else rightAlt++
+                }
+            }
+        }
+
+        assertTrue(rightAlt > leftAlt * 2, "High-variance region should receive more alternate colours")
+        assertTrue(rightAlt > (W * H) / 6, "Alternate fill should cover a meaningful portion of hiTex area")
+    }
+}
+
