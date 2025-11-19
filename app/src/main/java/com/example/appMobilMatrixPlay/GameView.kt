package com.example.appMobilMatrixPlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val handler = Handler(Looper.getMainLooper())
    private var sendMovementRunnable: Runnable? = null
    private var targetNormalizedY: Float = 0.5f
    private var currentNormalizedY: Float = 0.5f

    // Paints para los diferentes elementos
    private val paddleLeftPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val paddleRightPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val ballPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val centerLinePaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.STROKE
        strokeWidth = 2f
        alpha = 76 // 0.3 * 255
        isAntiAlias = true
    }

    private val sliderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        alpha = 128 // 0.5 * 255
        isAntiAlias = true
    }

    private val sliderThumbPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        alpha = 180
        isAntiAlias = true
    }

    // Posiciones y tamaños
    var paddleWidth = 12f
    var paddleHeight = 160f
    var paddleMargin = 20f
    var ballSize = 16f

    // Posiciones de las palas (normalizado 0-1)
    var leftPaddleY = 0.5f
    var rightPaddleY = 0.5f

    // Posición de la bola (normalizado 0-1)
    var ballX = 0.5f
    var ballY = 0.5f

    // Control del slider
    // Indica si la pala local es la izquierda. Separado de la información de juego
    // que puede indicar qué pala es 'left' en el servidor.
    var isLeftPlayer = true
    var localIsLeftPlayer = true
    var showSlider = true
    private var isDraggingSlider = false
    private val sliderWidth = 40f
    private val sliderThumbRadius = 14f
    // Reducir la altura útil del slider para evitar la bandeja de notificaciones
    private val sliderVerticalInset = 48f

    // Callback para cuando cambia la posición del slider
    var onPaddlePositionChanged: ((Float) -> Unit)? = null
    var onTouchReleased: (() -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Fondo (transparente, se ve el fondo del layout)
        canvas.drawColor(Color.TRANSPARENT)

        // Línea central punteada
        drawCenterLine(canvas)

        // Pala izquierda
        drawLeftPaddle(canvas)

        // Pala derecha
        drawRightPaddle(canvas)

        // Bola
        drawBall(canvas)

        // Slider (solo si está activo)
        if (showSlider) {
            drawSlider(canvas)
        }
    }

    private fun drawCenterLine(canvas: Canvas) {
        val centerX = width / 2f
        val dashHeight = 20f
        val dashGap = 15f
        var y = 0f

        while (y < height) {
            canvas.drawLine(centerX, y, centerX, (y + dashHeight).coerceAtMost(height.toFloat()), centerLinePaint)
            y += dashHeight + dashGap
        }
    }

    private fun drawLeftPaddle(canvas: Canvas) {
        val left = paddleMargin
        val top = (height * leftPaddleY) - (paddleHeight / 2)
        val right = left + paddleWidth
        val bottom = top + paddleHeight

        canvas.drawRect(left, top, right, bottom, paddleLeftPaint)
    }

    private fun drawRightPaddle(canvas: Canvas) {
        val right = width - paddleMargin
        val top = (height * rightPaddleY) - (paddleHeight / 2)
        val left = right - paddleWidth
        val bottom = top + paddleHeight

        canvas.drawRect(left, top, right, bottom, paddleRightPaint)
    }

    private fun drawBall(canvas: Canvas) {
        val centerX = width * ballX
        val centerY = height * ballY
        canvas.drawCircle(centerX, centerY, ballSize / 2, ballPaint)
    }

    private fun drawSlider(canvas: Canvas) {
        val sliderX = if (localIsLeftPlayer) sliderWidth else width - sliderWidth
        val sliderTop = (paddleHeight / 2) + sliderVerticalInset
        val sliderBottom = height - (paddleHeight / 2) - sliderVerticalInset

        // Línea del slider
        canvas.drawLine(sliderX, sliderTop, sliderX, sliderBottom, sliderPaint)

        // Thumb del slider
        val myPaddleY = if (localIsLeftPlayer) leftPaddleY else rightPaddleY
        val thumbY = sliderTop + (sliderBottom - sliderTop) * myPaddleY

        // Cambiar color del thumb según el jugador
        sliderThumbPaint.color = if (localIsLeftPlayer) Color.RED else Color.BLACK
        canvas.drawCircle(sliderX, thumbY, sliderThumbRadius, sliderThumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!showSlider) return super.onTouchEvent(event)

        val sliderX = if (localIsLeftPlayer) sliderWidth else width - sliderWidth
        val sliderTop = (paddleHeight / 2) + sliderVerticalInset
        val sliderBottom = height - (paddleHeight / 2) - sliderVerticalInset

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Verificar si el toque está cerca del slider
                val touchX = event.x
                val touchY = event.y
                val distance = Math.abs(touchX - sliderX)

                if (distance < sliderWidth && touchY >= sliderTop && touchY <= sliderBottom) {
                    isDraggingSlider = true
                    updatePaddlePosition(touchY, sliderTop, sliderBottom)
                    startSendingMovement()
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDraggingSlider) {
                    updatePaddlePosition(event.y, sliderTop, sliderBottom)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDraggingSlider = false
                stopSendingMovement()
                // Notificar que se soltó el touch
                onTouchReleased?.invoke()
            }
        }

        return super.onTouchEvent(event)
    }

    private fun updatePaddlePosition(touchY: Float, sliderTop: Float, sliderBottom: Float) {
        // Calcular posición normalizada (0-1)
        val normalizedY = ((touchY - sliderTop) / (sliderBottom - sliderTop)).coerceIn(0f, 1f)
        
        // Guardar posición objetivo
        targetNormalizedY = normalizedY

        // Actualizar la posición de mi pala visualmente INMEDIATAMENTE
        if (localIsLeftPlayer) {
            leftPaddleY = normalizedY
        } else {
            rightPaddleY = normalizedY
        }
        
        // Notificar el cambio INMEDIATAMENTE
        onPaddlePositionChanged?.invoke(normalizedY)

        // Redibujar
        invalidate()
    }
    
    private fun startSendingMovement() {
        // Ya no necesitamos este sistema complejo de envío continuo
        // El envío se hace inmediatamente en updatePaddlePosition con throttle en GestioMoviment
    }
    
    private fun stopSendingMovement() {
        // No necesitamos limpiar nada ya que no hay runnable
    }

    fun setBallColor(color: Int) {
        ballPaint.color = color
        invalidate()
    }

    fun updateBallPosition(x: Float, y: Float) {
        ballX = x.coerceIn(0f, 1f)
        ballY = y.coerceIn(0f, 1f)
        invalidate()
    }

    fun updateLeftPaddle(y: Float) {
        leftPaddleY = y.coerceIn(0f, 1f)
        invalidate()
    }

    fun updateRightPaddle(y: Float) {
        rightPaddleY = y.coerceIn(0f, 1f)
        invalidate()
    }
    
    // Mapea coordenada del servidor (0..400) a la posición central normalizada (0..1)
    private fun serverYToCenterNormalized(serverY: Int): Float {
        val edgeNormalized = (serverY / 400f).coerceIn(0f, 1f)
        // Si el view aún no tiene tamaño, fallback simple
        if (height <= 0) return edgeNormalized

        val paddleH = paddleHeight
        val minCenter = (paddleH / 2f) / height.toFloat()
        val maxCenter = (height - paddleH / 2f) / height.toFloat()

        val center = edgeNormalized * (maxCenter - minCenter) + minCenter
        return center.coerceIn(0f, 1f)
    }

    fun updateLeftPaddleFromServer(serverY: Int) {
        leftPaddleY = serverYToCenterNormalized(serverY)
        invalidate()
    }

    fun updateRightPaddleFromServer(serverY: Int) {
        rightPaddleY = serverYToCenterNormalized(serverY)
        invalidate()
    }

    // Función para obtener la posición actual de mi pala
    fun getMyPaddleY(): Float {
        return if (isLeftPlayer) leftPaddleY else rightPaddleY
    }
}
