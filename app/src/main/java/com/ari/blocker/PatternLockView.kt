package com.ari.blocker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

class PatternLockView(context: Context) : View(context) {
    var onPatternComplete: ((List<Int>) -> Unit)? = null

    private val points = Array(9) { Pair(0f, 0f) }
    private val selected = mutableListOf<Int>()
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tempPath = Path()

    // Tunable to match the reference pattern-lock screen.
    var gapRatio = 0.30f
    var dotRadiusRatio = 0.055f

    init {
        isClickable = true
        circlePaint.style = Paint.Style.FILL
        circlePaint.color = 0xFF67D8B5.toInt()
        selectedPaint.style = Paint.Style.FILL
        selectedPaint.color = 0xFF8FF0C9.toInt()
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = 7f * resources.displayMetrics.density
        linePaint.strokeCap = Paint.Cap.ROUND
        linePaint.strokeJoin = Paint.Join.ROUND
        linePaint.color = 0xFF67D8B5.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val size = minOf(w, h)
        val cx = w / 2f
        val cy = h / 2f
        val gap = size * gapRatio
        val r = size * dotRadiusRatio

        var index = 0
        for (row in 0..2) {
            for (col in 0..2) {
                points[index] = Pair(cx + (col - 1) * gap, cy + (row - 1) * gap)
                index++
            }
        }

        if (selected.isNotEmpty()) {
            tempPath.reset()
            val first = points[selected.first()]
            tempPath.moveTo(first.first, first.second)
            for (i in 1 until selected.size) {
                val p = points[selected[i]]
                tempPath.lineTo(p.first, p.second)
            }
            canvas.drawPath(tempPath, linePaint)
        }

        for (i in points.indices) {
            val p = points[i]
            canvas.drawCircle(p.first, p.second, r, if (selected.contains(i)) selectedPaint else circlePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            selected.clear()
            addPointAt(event.x, event.y)
            invalidate()
            return true
        }
        if (event.action == MotionEvent.ACTION_MOVE) {
            addPointAt(event.x, event.y)
            invalidate()
            return true
        }
        if (event.action == MotionEvent.ACTION_UP) {
            if (selected.size >= 4) {
                onPatternComplete?.invoke(selected.toList())
            } else {
                selected.clear()
                invalidate()
            }
            return true
        }
        return true
    }

    private fun addPointAt(x: Float, y: Float) {
        val size = minOf(width, height).toFloat()
        val radius = size * 0.11f
        points.forEachIndexed { index, p ->
            if (!selected.contains(index) && hypot((x - p.first).toDouble(), (y - p.second).toDouble()) <= radius) {
                selected.add(index)
                invalidate()
            }
        }
    }

    fun clearPattern() {
        selected.clear()
        invalidate()
    }
}
