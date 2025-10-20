package com.appforcross.editor.palette

/** Параметры квантования палитры для фото-ветки. */
data class QuantParams(
    val kStart: Int = 12,
    val kMax: Int = 44,
    val dE00Min: Float = 3.5f,
    val roiWeights: ROIWeights = ROIWeights(),
    val kneedleTau: Float = 0.015f
)

/** Весовые коэффициенты по ROI-категориям. */
data class ROIWeights(
    val edges: Float = 1.0f,
    val skin: Float = 0.8f,
    val sky: Float = 0.7f,
    val hitex: Float = 0.9f,
    val flat: Float = 0.5f
)

/** Результат квантования палитры. */
data class QuantResult(
    val colorsOKLab: FloatArray,
    val assignments: IntArray,
    val metrics: QuantMetrics
)

/** Метрики подобранной палитры. */
data class QuantMetrics(
    val K: Int,
    val avgDE: Float,
    val maxDE: Float,
    val minDE: Float,
    val p95DE: Float,
    val photoScoreStar: Float,
    val gbiProxy: Float
)
