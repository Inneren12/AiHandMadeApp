package com.appforcross.editor.tools

import com.appforcross.editor.data.BN64Spec
import com.appforcross.editor.data.BlueNoise64
import java.io.File

/**
 * Console tool (optional). Does nothing by itself in app runtime.
 * You may run it manually to produce a file:
 *
 * kotlinc BlueNoise64.kt BlueNoise64Tool.kt -include-runtime -d BlueNoise64Tool.jar
 * java -jar BlueNoise64Tool.jar --out blue_noise_64x64.bin --size 64 --passes 4 --sigma1 1.2 --sigma2 2.4 --seed 0xA1F00D01
 */
object BlueNoise64Tool {

    @JvmStatic
    fun main(args: Array<String>) {
        val argMap = parseArgs(args)
        val size   = (argMap["--size"] ?: "64").toInt()
        val passes = (argMap["--passes"] ?: "4").toInt()
        val sigma1 = (argMap["--sigma1"] ?: "1.2").toDouble()
        val sigma2 = (argMap["--sigma2"] ?: "2.4").toDouble()
        val seed   = decodeLong(argMap["--seed"] ?: "0xA1F00D01")
        val out    = argMap["--out"] // optional

        val spec = BN64Spec(size = size, passes = passes, sigma1 = sigma1, sigma2 = sigma2, seed = seed)
        val bytes = BlueNoise64.generate(spec)

        if (out != null) {
            val f = File(out)
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
            println("""{"lvl":"I","tag":"ASSETS","evt":"write","data":{"path":"${f.absolutePath}","bytes":${bytes.size}}}""")
        } else {
            // No file is produced unless --out is explicitly provided.
            println("""{"lvl":"I","tag":"ASSETS","evt":"note","data":{"info":"No --out specified; bytes kept in memory only"}}""")
        }
    }

    // --- helpers ---
    private fun parseArgs(argv: Array<String>): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        var i = 0
        while (i < argv.size) {
            val k = argv[i]
            if (k.startsWith("--") && i + 1 < argv.size && !argv[i + 1].startsWith("--")) {
                m[k] = argv[i + 1]; i += 2
            } else {
                m[k] = ""; i += 1
            }
        }
        return m
    }
    private fun decodeLong(s: String): Long =
        try { java.lang.Long.decode(s) } catch (_: Throwable) { s.toLong() }
}
