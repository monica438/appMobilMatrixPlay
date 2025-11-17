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
        private const val SEND_INTERVAL = 50L // Enviar cada 50ms mientras se arrastra
    }
    
    private var direccioActual = "none"
    private val handler = Handler(Looper.getMainLooper())
    private var continuousSendRunnable: Runnable? = null
    
    fun enviarDireccio(direccio: String) {
        direccioActual = direccio
        val json = JSONObject()
        json.put("type", "move")
        json.put("direction", direccio)
        wsClient.sendJSON(json)
        Log.d(TAG, "📤 Enviando: type=move, direction=$direccio")
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
    
    // Para compatibilidad con touch events (llamado desde GameView)
    private var lastY: Float = 0.5f
    
    fun handleTouchMove(normalizedY: Float) {
        val direction = when {
            normalizedY < lastY - 0.05f -> "up"
            normalizedY > lastY + 0.05f -> "down"   
            else -> return // No enviar si el cambio es muy pequeño
        }
        lastY = normalizedY
        enviarDireccio(direction)
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