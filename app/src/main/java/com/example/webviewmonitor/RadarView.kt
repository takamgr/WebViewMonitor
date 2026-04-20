package com.example.webviewmonitor

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator

class RadarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class RadarState { MONITORING, SESSION_EXPIRED, VACANCY }

    private var currentState = RadarState.MONITORING
    private var currentColor = Color.parseColor("#00E676")
    private var showReloadDot = false
    private val handler = Handler(Looper.getMainLooper())

    private val paintCircle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = currentColor
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val paintSweep = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val paintRipple = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = currentColor
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val paintDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var sweepAngle = 0f
    private var rippleRadius = 0f
    private var rippleAlpha = 0
    private var overlayBitmap: Bitmap? = null
    private var stateBitmap: Bitmap? = null

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

    fun setState(state: RadarState) {
        currentState = state
        currentColor = when (state) {
            RadarState.MONITORING      -> Color.parseColor("#00E676")
            RadarState.SESSION_EXPIRED -> Color.parseColor("#FFD600")
            RadarState.VACANCY         -> Color.parseColor("#FF1744")
        }
        paintCircle.color = currentColor
        paintRipple.color = currentColor
        stateBitmap = when (state) {
            RadarState.SESSION_EXPIRED ->
                BitmapFactory.decodeResource(context.resources, R.drawable.radar_session_expired)
            RadarState.VACANCY ->
                BitmapFactory.decodeResource(context.resources, R.drawable.radar_vacancy)
            RadarState.MONITORING -> null
        }
        invalidate()
    }

    fun startRadar(bitmap: Bitmap? = null, state: RadarState = RadarState.MONITORING) {
        overlayBitmap = bitmap
        setState(state)
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

    fun triggerReloadDot() {
        showReloadDot = true
        invalidate()
        handler.postDelayed({
            showReloadDot = false
            invalidate()
        }, 500)
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
        val sweepColor = Color.argb(
            128,
            Color.red(currentColor),
            Color.green(currentColor),
            Color.blue(currentColor)
        )
        val sweepGradient = SweepGradient(
            cx, cy,
            intArrayOf(Color.TRANSPARENT, sweepColor),
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

        // PNG中央表示（MONITORINGのみ）
        if (currentState == RadarState.MONITORING) {
            overlayBitmap?.let { bmp ->
                val displayW = maxR * 1.8f
                val displayH = displayW * bmp.height / bmp.width
                val left = cx - displayW / 2f
                val top = cy - displayH / 2f
                canvas.drawBitmap(bmp, null, RectF(left, top, left + displayW, top + displayH), null)
            }
        }

        // 状態別PNG（SESSION_EXPIRED・VACANCY）
        stateBitmap?.let { bmp ->
            val displayW = maxR * 1.8f
            val displayH = displayW * bmp.height / bmp.width
            val left = cx - displayW / 2f
            val top = cy - displayH / 2f
            canvas.drawBitmap(bmp, null, RectF(left, top, left + displayW, top + displayH), null)
        }

        // リロード小丸（固定位置：135度・maxR×0.5）
        if (showReloadDot) {
            val dotRadius = 8f * context.resources.displayMetrics.density
            val dotRad = Math.toRadians(135.0)
            val dotX = cx + (maxR * 0.5f * Math.cos(dotRad)).toFloat()
            val dotY = cy + (maxR * 0.5f * Math.sin(dotRad)).toFloat()
            paintDot.color = Color.parseColor("#99FF1744")
            canvas.drawCircle(dotX, dotY, dotRadius, paintDot)
        }
    }
}
