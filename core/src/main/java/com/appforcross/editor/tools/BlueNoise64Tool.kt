package com.appforcross.editor.tools

import com.appforcross.editor.data.BN64Spec
import com.appforcross.editor.data.BlueNoise64
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Console entry point for generating 64×64 blue-noise maps on demand.
 */
object BlueNoise64Tool {
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            val options = parseArgs(args)
            val defaults = BN64Spec()
            val spec = BN64Spec(
                size = options.size ?: defaults.size,
                passes = options.passes ?: defaults.passes,
                sigma1 = options.sigma1 ?: defaults.sigma1,
                sigma2 = options.sigma2 ?: defaults.sigma2,
                seed = options.seed ?: defaults.seed,
                method = defaults.method
            )
            val bytes = BlueNoise64.generate(spec)
            options.out?.let { path ->
                writeToFile(path, bytes)
            }
            val hash = BlueNoise64.sha256(bytes)
            println("BlueNoise64 generated ${bytes.size} bytes, sha256=$hash")
        } catch (ex: IllegalArgumentException) {
            System.err.println("Error: ${ex.message}")
            printUsage()
            exitProcess(1)
        }
    }

    private fun parseArgs(args: Array<String>): Options {
        var index = 0
        val options = Options()
        while (index < args.size) {
            when (val arg = args[index]) {
                "--out" -> {
                    index++
                    require(index < args.size) { "--out requires a path" }
                    options.out = args[index]
                }
                "--size" -> {
                    options.size = readInt(args, ++index, "--size")
                }
                "--passes" -> {
                    options.passes = readInt(args, ++index, "--passes")
                }
                "--sigma1" -> {
                    options.sigma1 = readDouble(args, ++index, "--sigma1")
                }
                "--sigma2" -> {
                    options.sigma2 = readDouble(args, ++index, "--sigma2")
                }
                "--seed" -> {
                    options.seed = readLong(args, ++index, "--seed")
                }
                "--help", "-h" -> {
                    printUsage()
                    exitProcess(0)
                }
                else -> throw IllegalArgumentException("Unknown argument: $arg")
            }
            index++
        }
        return options
    }

    private fun readInt(args: Array<String>, index: Int, flag: String): Int {
        require(index < args.size) { "$flag requires a value" }
        return args[index].toInt()
    }

    private fun readDouble(args: Array<String>, index: Int, flag: String): Double {
        require(index < args.size) { "$flag requires a value" }
        return args[index].toDouble()
    }

    private fun readLong(args: Array<String>, index: Int, flag: String): Long {
        require(index < args.size) { "$flag requires a value" }
        val token = args[index]
        return if (token.startsWith("0x", ignoreCase = true)) {
            token.substring(2).toLong(16)
        } else {
            token.toLong()
        }
    }

    private fun writeToFile(path: String, bytes: ByteArray) {
        val target = Path.of(path)
        target.parent?.let { parent ->
            Files.createDirectories(parent)
        }
        Files.write(target, bytes)
    }

    private fun printUsage() {
        println(
            "Usage: BlueNoise64Tool --out <path> [--size N] [--passes N] " +
                "[--sigma1 F] [--sigma2 F] [--seed N]"
        )
    }

    private class Options {
        var out: String? = null
        var size: Int? = null
        var passes: Int? = null
        var sigma1: Double? = null
        var sigma2: Double? = null
        var seed: Long? = null
    }
}
