package com.appforcross.editor.ui

import com.appforcross.editor.prescale.PreScaleOrchestrator
import com.appforcross.editor.prescale.PreScaleReport
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/** Deterministic worker: single background thread executes tasks sequentially. */
class PreScaleWorker {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(namedThreadFactory())

    fun run(
        rgb: FloatArray,
        width: Int,
        height: Int,
        forceLargerWst: Boolean,
        onResult: (PreScaleReport) -> Unit
    ) {
        executor.execute {
            val report = PreScaleOrchestrator.run(rgb, width, height, forceLargerWst)
            onResult(report)
        }
    }

    fun dispose() {
        executor.shutdownNow()
    }

    private fun namedThreadFactory(): ThreadFactory {
        return ThreadFactory { runnable ->
            Thread(runnable, "PreScaleWorker").apply { isDaemon = true }
        }
    }
}
