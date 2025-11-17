package com.example.appMobilMatrixPlay

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject

class GestioMoviment(
    private val wsClient: WebSocketClient, 
    private val getMyPaddleY: () -> Float,
    private val getMyServerColor: () -> String
) {
    
    companion object {
        private const val TAG = "GestioMoviment"
    }
    
    private var direccioActual = "none"
    private val handler = Handler(Looper.getMainLooper())
    private val throttleRunnable = Runnable { /* solo para throttling */ }
    private var lastSendTime: Long = 0
    private val THROTTLE_DELAY = 50L
    
    fun enviarDireccio(direccio: String) {
        val currentTime = System.currentTimeMillis()
        
        if ((currentTime - lastSendTime) > THROTTLE_DELAY) {
            direccioActual = direccio
            
            // Obtener la posición actual de mi pala (normalizada 0-1)
            val normalizedY = getMyPaddleY()
            // Convertir a coordenadas virtuales del servidor (0-400)
            val serverY = (normalizedY * 400.0).toInt()
            
            // Obtener mi color del servidor (VERMELL o NEGRE)
            val serverColor = getMyServerColor()
            
            val json = JSONObject().apply {
                put("type", "move")
                put("direction", direccio)
                put("y", serverY)
                put("color", serverColor)
                put("timestamp", currentTime)
            }
            
            wsClient.sendJSON(json)
            lastSendTime = currentTime
            Log.d(TAG, "📤 Movimiento enviado: $direccio, Y: $serverY, Color: $serverColor")
            
            handler.removeCallbacks(throttleRunnable)
            handler.postDelayed(throttleRunnable, THROTTLE_DELAY)
        }
    }
    
    fun handleKeyEvent(isPressed: Boolean, isUp: Boolean) {
        if (isPressed) {
            direccioActual = if (isUp) "up" else "down"
            enviarDireccio(direccioActual)
        } else {
            val expectedDirection = if (isUp) "up" else "down"
            if (direccioActual == expectedDirection) {
                direccioActual = "none"
                enviarDireccio(direccioActual)
            }
        }
    }
    
    fun handleTouchMove(normalizedY: Float, previousY: Float) {
        val delta = normalizedY - previousY
        
        val newDirection = when {
            delta < -0.01f -> "up"
            delta > 0.01f -> "down"
            else -> "none"
        }
        
        Log.d(TAG, "👆 handleTouchMove: normalizedY=$normalizedY, previousY=$previousY, delta=$delta, newDirection=$newDirection")
        
        if (newDirection != direccioActual) {
            direccioActual = newDirection
            enviarDireccio(newDirection)
        }
    }
    
    fun stopMovement() {
        if (direccioActual != "none") {
            direccioActual = "none"
            enviarDireccio("none")
            Log.d(TAG, "⏹️ Movimiento detenido")
        }
    }
    
    fun getDireccionActual(): String = direccioActual
}