package com.example.appMobilMatrixPlay

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject

class GestioMoviment(
    private val wsClient: WebSocketClient
) {
    
    companion object {
        private const val TAG = "GestioMoviment"
        private const val SEND_INTERVAL = 8L // Enviar cada 8ms (~120fps) para máxima velocidad
    }
    
    private var direccioActual = "none"
    private val handler = Handler(Looper.getMainLooper())
    private var continuousSendRunnable: Runnable? = null
    
    fun enviarDireccio(direccio: String) {
        val json = JSONObject()
        json.put("type", "move")
        json.put("direction", direccio)
        wsClient.sendJSON(json)
        // Log.d(TAG, "📤 Enviando: type=move, direction=$direccio")  // Demasiados logs
    }
    
    fun enviarPosicion(y: Int) {
        val yClamped = y.coerceIn(0, 400)
        val json = JSONObject()
        json.put("type", "position")
        json.put("y", yClamped)
        wsClient.sendJSON(json)
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
    
    // Para touch: enviar posición exacta
    private var lastY: Float = 0f
    private var currentDirection: String = "none"
    
    fun handleTouchMove(serverY: Float) {
        // Enviar posición exacta al servidor (0..400)
        val y = serverY.toInt()
        enviarPosicion(y)

        lastY = serverY
    }
    
    private fun startContinuousSend() {
        continuousSendRunnable = object : Runnable {
            override fun run() {
                if (currentDirection != "none") {
                    enviarDireccio(currentDirection)
                    handler.postDelayed(this, SEND_INTERVAL)
                }
            }
        }
        handler.postDelayed(continuousSendRunnable!!, SEND_INTERVAL)
    }
    
    private fun stopContinuousSend() {
        continuousSendRunnable?.let {
            handler.removeCallbacks(it)
            continuousSendRunnable = null
        }
    }
    
    fun stopMovement() {
        // Solo necesitamos enviar la última posición conocida
        enviarPosicion(lastY.toInt())
        Log.d(TAG, "⏹️ Movimiento detenido - Última posición: ${lastY.toInt()}")
    }
    
    fun getDireccionActual(): String = direccioActual
}