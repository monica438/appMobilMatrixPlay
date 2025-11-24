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
    var paddleWidth = 30f
    var paddleHeight = 100f
    var paddleMargin = 60f
    var ballSize = 22f
    
    // Dimensiones del servidor
    var serverVirtualHeight = 400f
    var serverVirtualWidth = 600f
    var serverPaddleHeight = 100f
        set(value) {
            field = value
            updateDimensions()
        }
    var serverPaddleWidth = 15f
        set(value) {
            field = value
            updateDimensions()
        }
    var serverBallSize = 22f
        set(value) {
            field = value
            updateDimensions()
        }

    // Posiciones de las palas (normalizado 0-1)
    var leftPaddleY = 0.5f
    var rightPaddleY = 0.5f
    var leftPaddleX = 0.033f // Default P1 X (20/600)
    var rightPaddleX = 0.95f // Default P2 X (570/600)

    // Posición de la bola (normalizado 0-1, Top-Left)
    var ballX = 0.5f
    var ballY = 0.5f

    // Control del slider
    // Indica si la pala local es la izquierda. Separado de la información de juego
    // que puede indicar qué pala es 'left' en el servidor.
    var isLeftPlayer = true
    var localIsLeftPlayer = true
    var showSlider = true
    private var isDraggingSlider = false
    private val sliderWidth = 45f
    private val sliderThumbRadius = 14f
    // Reducir la altura útil del slider para evitar la bandeja de notificaciones
    private val sliderVerticalInset = 48f

    // Callback para cuando cambia la posición del slider
    var onPaddlePositionChanged: ((Float) -> Unit)? = null
    var onTouchReleased: (() -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateDimensions()
    }
    
    private fun updateDimensions() {
        if (height > 0 && width > 0) {
            paddleHeight = (serverPaddleHeight / serverVirtualHeight) * height
            paddleWidth = (serverPaddleWidth / serverVirtualWidth) * width
            ballSize = (serverBallSize / serverVirtualWidth) * width // Escalar bola con ancho para mantener proporción
            invalidate()
        }
    }

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
        // Usar posición X normalizada
        val left = width * leftPaddleX
        val right = left + paddleWidth

        // Calcular centro Y en pixeles y asegurarnos que la pala queda dentro de la pantalla
        val centerY = (height * leftPaddleY).coerceIn(paddleHeight / 2f, height - paddleHeight / 2f)
        val top = centerY - (paddleHeight / 2f)
        val bottom = centerY + (paddleHeight / 2f)

        canvas.drawRect(left, top, right, bottom, paddleLeftPaint)
    }

    private fun drawRightPaddle(canvas: Canvas) {
        // Usar posición X normalizada
        val left = width * rightPaddleX
        val right = left + paddleWidth

        // Calcular centro Y en pixeles y asegurarnos que la pala queda dentro de la pantalla
        val centerY = (height * rightPaddleY).coerceIn(paddleHeight / 2f, height - paddleHeight / 2f)
        val top = centerY - (paddleHeight / 2f)
        val bottom = centerY + (paddleHeight / 2f)

        canvas.drawRect(left, top, right, bottom, paddleRightPaint)
    }

    private fun drawBall(canvas: Canvas) {
        // ballX y ballY son coordenadas Top-Left normalizadas
        val left = width * ballX
        val top = height * ballY
        
        // drawCircle dibuja desde el centro, así que desplazamos
        val radius = ballSize / 2f
        val centerX = left + radius
        val centerY = top + radius
        
        canvas.drawCircle(centerX, centerY, radius, ballPaint)
    }

    private fun drawSlider(canvas: Canvas) {
        val sliderX = if (localIsLeftPlayer) sliderWidth else width - sliderWidth
        val sliderTop = (paddleHeight / 2) + sliderVerticalInset
        val sliderBottom = height - (paddleHeight / 2) - sliderVerticalInset

        // Línea del slider
        canvas.drawLine(sliderX, sliderTop, sliderX, sliderBottom, sliderPaint)

        // Thumb del slider
        val myPaddleY = if (localIsLeftPlayer) leftPaddleY else rightPaddleY
        val edgeNormalized = centerToEdgeNormalized(myPaddleY)
        val thumbY = sliderTop + edgeNormalized * (sliderBottom - sliderTop)

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
        val normalizedInSlider = ((touchY - sliderTop) / (sliderBottom - sliderTop)).coerceIn(0f, 1f)

        // Mapear edge-normalized (0..1) a center-normalized (0..1) para la pala
        val centerNormalized = edgeToCenterNormalized(normalizedInSlider)

        // Guardar posición objetivo (centro normalizado) y clamar dentro de los límites posibles
        val minCenter = (paddleHeight / 2f) / height.toFloat()
        val maxCenter = (height - paddleHeight / 2f) / height.toFloat()
        val clampedCenter = centerNormalized.coerceIn(minCenter, maxCenter)

        targetNormalizedY = clampedCenter

        if (localIsLeftPlayer) {
            leftPaddleY = clampedCenter
        } else {
            rightPaddleY = clampedCenter
        }

        // Calcular posición Y para el servidor (Top-Left en coordenadas 0..400)
        // normalizedInSlider 0 -> Top (0)
        // normalizedInSlider 1 -> Bottom (400 - serverPaddleHeight)
        val serverY = normalizedInSlider * (serverVirtualHeight - serverPaddleHeight)

        // Notificar el cambio con la coordenada Y del servidor
        onPaddlePositionChanged?.invoke(serverY)

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
    
    // Mapea coordenada del servidor (0..400, Top-Left) a la posición central normalizada (0..1)
    private fun serverYToCenterNormalized(serverY: Int): Float {
        // CenterY en servidor = serverY + serverPaddleHeight / 2
        val centerYServer = serverY + serverPaddleHeight / 2f
        val centerNormalized = centerYServer / serverVirtualHeight
        return centerNormalized.coerceIn(0f, 1f)
    }

    fun updateLeftPaddleFromServer(serverY: Int) {
        leftPaddleY = serverYToCenterNormalized(serverY)
        invalidate()
    }

    fun updateRightPaddleFromServer(serverY: Int) {
        rightPaddleY = serverYToCenterNormalized(serverY)
        invalidate()
    }

    // Convierte edge-normalized (0..1, 0=top borde de la pala) a center-normalized (0..1)
    private fun edgeToCenterNormalized(edge: Float): Float {
        val e = edge.coerceIn(0f, 1f)
        if (height <= 0) return e
        val minCenter = (paddleHeight / 2f) / height.toFloat()
        val maxCenter = (height - paddleHeight / 2f) / height.toFloat()
        return (e * (maxCenter - minCenter) + minCenter).coerceIn(0f, 1f)
    }

    // Convierte center-normalized a edge-normalized (inversa)
    private fun centerToEdgeNormalized(center: Float): Float {
        if (height <= 0) return center.coerceIn(0f, 1f)
        val minCenter = (paddleHeight / 2f) / height.toFloat()
        val maxCenter = (height - paddleHeight / 2f) / height.toFloat()
        val c = center.coerceIn(minCenter, maxCenter)
        val denom = (maxCenter - minCenter).takeIf { it != 0f } ?: 1f
        return ((c - minCenter) / denom).coerceIn(0f, 1f)
    }

    // Función para obtener la posición actual de mi pala
    fun getMyPaddleY(): Float {
        return if (isLeftPlayer) leftPaddleY else rightPaddleY
    }
}
