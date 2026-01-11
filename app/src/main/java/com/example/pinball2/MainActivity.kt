package com.example.pinball2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.app.Activity
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(PinballView(this))
    }
}

private class PinballView(context: Context) : View(context) {
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 20, 20) }
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(40, 120, 170)
        style = Paint.Style.STROKE
        strokeWidth = 12f
    }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val plungerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(200, 180, 40) }
    private val paddlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(220, 90, 90) }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(200, 200, 200)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val boardRect = RectF()
    private val ball = Ball()
    private val leftPaddle = Paddle(isLeft = true)
    private val rightPaddle = Paddle(isLeft = false)

    private var plungerPull = 0f
    private var plungerHeld = false

    private var lastFrameTime = 0L

    init {
        setBackgroundColor(Color.BLACK)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val padding = min(w, h) * 0.06f
        boardRect.set(padding, padding, w - padding, h - padding)
        resetBall()
        leftPaddle.configure(boardRect)
        rightPaddle.configure(boardRect)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(boardRect, boardPaint)
        drawPlunger(canvas)
        drawPaddle(canvas, leftPaddle)
        drawPaddle(canvas, rightPaddle)
        canvas.drawCircle(ball.x, ball.y, ball.radius, ballPaint)
    }

    private fun drawPlunger(canvas: Canvas) {
        val laneWidth = boardRect.width() * 0.15f
        val laneLeft = boardRect.right - laneWidth
        val laneRect = RectF(laneLeft, boardRect.top, boardRect.right, boardRect.bottom)
        canvas.drawRect(laneRect, guidePaint)

        val plungerHeight = boardRect.height() * 0.18f
        val plungerTop = boardRect.bottom - plungerHeight + plungerPull
        val plungerRect = RectF(
            laneLeft + laneWidth * 0.2f,
            plungerTop,
            boardRect.right - laneWidth * 0.2f,
            boardRect.bottom
        )
        canvas.drawRect(plungerRect, plungerPaint)
    }

    private fun drawPaddle(canvas: Canvas, paddle: Paddle) {
        paddlePaint.strokeWidth = paddle.thickness
        canvas.drawLine(paddle.startX, paddle.startY, paddle.endX, paddle.endY, paddlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (isInPlungerLane(x, y)) {
                    plungerHeld = true
                    plungerPull = 0f
                } else if (x < width / 2f) {
                    leftPaddle.isFlipped = true
                } else {
                    rightPaddle.isFlipped = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (plungerHeld) {
                    val maxPull = boardRect.height() * 0.16f
                    val pull = max(0f, min(maxPull, boardRect.bottom - y))
                    plungerPull = pull
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (plungerHeld) {
                    releasePlunger()
                    plungerHeld = false
                    plungerPull = 0f
                }
                leftPaddle.isFlipped = false
                rightPaddle.isFlipped = false
            }
        }
        return true
    }

    private fun isInPlungerLane(x: Float, y: Float): Boolean {
        val laneWidth = boardRect.width() * 0.15f
        val laneLeft = boardRect.right - laneWidth
        return x >= laneLeft && x <= boardRect.right && y >= boardRect.bottom - boardRect.height() * 0.35f
    }

    private fun releasePlunger() {
        val laneWidth = boardRect.width() * 0.15f
        val laneLeft = boardRect.right - laneWidth
        val laneCenterX = laneLeft + laneWidth / 2f
        if (ball.x > laneLeft && ball.y > boardRect.bottom - boardRect.height() * 0.22f) {
            ball.x = laneCenterX
            ball.vy -= plungerPull * 10f
            ball.vx += (Math.random().toFloat() - 0.5f) * 150f
        }
    }

    private fun resetBall() {
        val laneWidth = boardRect.width() * 0.15f
        val laneLeft = boardRect.right - laneWidth
        ball.radius = min(boardRect.width(), boardRect.height()) * 0.025f
        ball.x = laneLeft + laneWidth / 2f
        ball.y = boardRect.bottom - ball.radius * 2.2f
        ball.vx = 0f
        ball.vy = 0f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrameTime = SystemClock.elapsedRealtime()
        postOnAnimation(frameRunnable)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frameRunnable)
        super.onDetachedFromWindow()
    }

    private val frameRunnable = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val dt = ((now - lastFrameTime).coerceAtMost(32L)) / 1000f
            lastFrameTime = now
            updatePhysics(dt)
            invalidate()
            postOnAnimation(this)
        }
    }

    private fun updatePhysics(dt: Float) {
        val gravity = boardRect.height() * 1.2f
        ball.vy += gravity * dt
        ball.vx *= 0.998f
        ball.vy *= 0.998f
        ball.x += ball.vx * dt
        ball.y += ball.vy * dt

        keepBallInBounds()
        leftPaddle.update(boardRect)
        rightPaddle.update(boardRect)
        resolvePaddleCollision(leftPaddle)
        resolvePaddleCollision(rightPaddle)
    }

    private fun keepBallInBounds() {
        if (ball.x - ball.radius < boardRect.left) {
            ball.x = boardRect.left + ball.radius
            ball.vx = abs(ball.vx)
        }
        if (ball.x + ball.radius > boardRect.right) {
            ball.x = boardRect.right - ball.radius
            ball.vx = -abs(ball.vx)
        }
        if (ball.y - ball.radius < boardRect.top) {
            ball.y = boardRect.top + ball.radius
            ball.vy = abs(ball.vy)
        }
        if (ball.y + ball.radius > boardRect.bottom) {
            ball.y = boardRect.bottom - ball.radius
            ball.vy = -abs(ball.vy)
        }
    }

    private fun resolvePaddleCollision(paddle: Paddle) {
        val nearest = paddle.nearestPoint(ball.x, ball.y)
        val dist = hypot(ball.x - nearest.first, ball.y - nearest.second)
        if (dist <= ball.radius + paddle.thickness / 2f) {
            val nx = (ball.x - nearest.first) / max(dist, 0.001f)
            val ny = (ball.y - nearest.second) / max(dist, 0.001f)
            val relativeSpeed = ball.vx * nx + ball.vy * ny
            if (relativeSpeed < 0f) {
                val bounce = 1.05f
                ball.vx -= (1 + bounce) * relativeSpeed * nx
                ball.vy -= (1 + bounce) * relativeSpeed * ny
                val separation = ball.radius + paddle.thickness / 2f - dist
                ball.x += nx * separation
                ball.y += ny * separation
                val boost = if (paddle.isFlipped) boardRect.height() * 0.45f else 0f
                ball.vx += nx * boost * 0.02f
                ball.vy += ny * boost * 0.02f
            }
        }
    }

    private class Ball {
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var radius = 0f
    }

    private class Paddle(private val isLeft: Boolean) {
        var startX = 0f
        var startY = 0f
        var endX = 0f
        var endY = 0f
        var thickness = 18f
        var isFlipped = false

        private var baseAngle = 0f
        private var flipAngle = 0f
        private var length = 0f
        private var pivotX = 0f
        private var pivotY = 0f

        fun configure(board: RectF) {
            thickness = min(board.width(), board.height()) * 0.02f
            length = board.width() * 0.22f
            val y = board.bottom - board.height() * 0.18f
            val offset = board.width() * 0.12f
            pivotX = if (isLeft) board.left + offset else board.right - offset
            pivotY = y
            baseAngle = if (isLeft) -25f else 205f
            flipAngle = if (isLeft) -65f else 245f
            update(board)
        }

        fun update(board: RectF) {
            val angleDeg = if (isFlipped) flipAngle else baseAngle
            val angle = Math.toRadians(angleDeg.toDouble()).toFloat()
            startX = pivotX
            startY = pivotY
            endX = pivotX + length * cos(angle)
            endY = pivotY + length * sin(angle)
        }

        fun nearestPoint(x: Float, y: Float): Pair<Float, Float> {
            val vx = endX - startX
            val vy = endY - startY
            val lenSq = vx * vx + vy * vy
            if (lenSq <= 0.0001f) return Pair(startX, startY)
            val t = ((x - startX) * vx + (y - startY) * vy) / lenSq
            val clamped = t.coerceIn(0f, 1f)
            return Pair(startX + clamped * vx, startY + clamped * vy)
        }
    }
}
