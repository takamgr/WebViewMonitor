package com.example.webviewmonitor

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator

class RadarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paintCircle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF41") // 変更
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val paintSweep = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val paintRipple = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF41") // 変更
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private var sweepAngle = 0f
    private var rippleRadius = 0f
    private var rippleAlpha = 0
    private var overlayBitmap: Bitmap? = null

    private val sweepAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            sweepAngle = it.animatedValue as Float
            invalidate()
        }
    }

    private val rippleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            val f = it.animatedFraction
            rippleRadius = f
            rippleAlpha = ((1f - f) * 180).toInt()
        }
    }

    fun startRadar(bitmap: Bitmap? = null) {
        overlayBitmap = bitmap
        sweepAnimator.start()
        rippleAnimator.start()
    }

    fun stopRadar() {
        sweepAnimator.cancel()
        rippleAnimator.cancel()
    }

    fun updateStatus(status: String, count: String) {
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxR = minOf(cx, cy) * 0.85f

        canvas.drawColor(Color.BLACK)

        // 同心円
        for (i in 1..4) {
            val r = maxR * i / 4f
            paintCircle.alpha = 80
            paintCircle.strokeWidth = 2f
            canvas.drawCircle(cx, cy, r, paintCircle)
        }

        // 十字線
        paintCircle.alpha = 50
        canvas.drawLine(cx - maxR, cy, cx + maxR, cy, paintCircle)
        canvas.drawLine(cx, cy - maxR, cx, cy + maxR, paintCircle)

        // スイープ
        val sweepGradient = SweepGradient(
            cx, cy,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#8000FF41")), // 変更
            floatArrayOf(0f, 1f)
        )
        val matrix = Matrix()
        matrix.setRotate(sweepAngle, cx, cy)
        sweepGradient.setLocalMatrix(matrix)
        paintSweep.shader = sweepGradient
        canvas.drawCircle(cx, cy, maxR, paintSweep)

        // レーダーライン
        paintCircle.alpha = 255
        paintCircle.strokeWidth = 3f
        val rad = Math.toRadians(sweepAngle.toDouble())
        canvas.drawLine(
            cx, cy,
            cx + (maxR * Math.cos(rad)).toFloat(),
            cy + (maxR * Math.sin(rad)).toFloat(),
            paintCircle
        )

        // 波紋
        paintRipple.alpha = rippleAlpha
        canvas.drawCircle(cx, cy, maxR * rippleRadius, paintRipple)

        // PNG中央表示
        overlayBitmap?.let { bmp ->
            val displayW = maxR * 1.8f // 変更：レーダー幅より少し小さく
            val displayH = displayW * bmp.height / bmp.width // 変更：縦横比維持
            val left = cx - displayW / 2f
            val top = cy - displayH / 2f
            val dst = RectF(left, top, left + displayW, top + displayH)
            canvas.drawBitmap(bmp, null, dst, null)
        }
    }
}
