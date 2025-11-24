package com.example.appMobilMatrixPlay

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ============================================================================
// WebSocketClient
// ============================================================================

class WebSocketClient(private val url: String) {
    
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    
    private val handler = Handler(Looper.getMainLooper())
    
    private var onMessageCallback: ((String) -> Unit)? = null
    private var onOpenCallback: (() -> Unit)? = null
    private var onCloseCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    
    private var shouldReconnect = true
    private var conectado = false
    private var heartbeatRunnable: Runnable? = null
    
    fun connect() {
        val request = Request.Builder()
            .url(url)
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                conectado = true
                Log.d(TAG, "✅ WebSocket CONNECTED: $url")
                handler.post {
                    startHeartbeat()
                    onOpenCallback?.invoke()
                }
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "📩 Received raw: $text")

                try {
                    val json = JSONObject(text)
                    val keys = json.keys()
                    val details = StringBuilder()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        details.append("$k=${json.opt(k)}; ")
                    }
                    Log.d(TAG, "📊 Received parsed: ${details}")
                } catch (e: Exception) {
                    // Not JSON
                }

                handler.post {
                    onMessageCallback?.invoke(text)
                }
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                conectado = false
                Log.d(TAG, "WebSocket closing: code=$code reason=$reason")
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                conectado = false
                Log.d(TAG, "WebSocket closed: code=$code reason=$reason")
                stopHeartbeat()
                handler.post {
                    onCloseCallback?.invoke()
                }
                
                if (shouldReconnect) {
                    scheduleReconnect()
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                conectado = false
                Log.d(TAG, "❌ WebSocket failure: ${t.message}")
                stopHeartbeat()
                handler.post {
                    onErrorCallback?.invoke(t.message ?: "Unknown error")
                }
                
                if (shouldReconnect) {
                    scheduleReconnect()
                }
            }
        })
    }
    
    private fun startHeartbeat() {
        heartbeatRunnable = object : Runnable {
            override fun run() {
                if (conectado) {
                    val ping = JSONObject().apply {
                        put("type", "ping")
                        put("timestamp", System.currentTimeMillis())
                    }
                    sendJSON(ping)
                    Log.d(TAG, "💓 Heartbeat sent")
                }
                handler.postDelayed(this, HEARTBEAT_INTERVAL)
            }
        }
        handler.post(heartbeatRunnable!!)
    }
    
    private fun stopHeartbeat() {
        heartbeatRunnable?.let {
            handler.removeCallbacks(it)
        }
        heartbeatRunnable = null
    }
    
    private fun scheduleReconnect() {
        handler.postDelayed({
            if (shouldReconnect && !conectado) {
                Log.d(TAG, "🔄 Attempting reconnect...")
                connect()
            }
        }, RECONNECT_DELAY)
    }
    
    fun send(message: String) {
        if (conectado) {
            Log.d(TAG, "📤 Sending: $message")
            webSocket?.send(message)
        } else {
            Log.d(TAG, "🚫 Send skipped, not connected: $message")
        }
    }
    
    fun sendJSON(json: JSONObject) {
        val jsonString = json.toString()
        Log.d(TAG, "📤 Enviando JSON: $jsonString")
        send(jsonString)
    }
    
    fun disconnect() {
        shouldReconnect = false
        conectado = false
        stopHeartbeat()
        webSocket?.close(1000, "Client closing")
        webSocket = null
        Log.d(TAG, "🔴 WebSocket disconnected")
    }
    
    fun onMessage(callback: (String) -> Unit) {
        onMessageCallback = callback
    }
    
    fun onOpen(callback: () -> Unit) {
        onOpenCallback = callback
    }
    
    fun onClose(callback: () -> Unit) {
        onCloseCallback = callback
    }
    
    fun onError(callback: (String) -> Unit) {
        onErrorCallback = callback
    }
    
    fun isConnected(): Boolean = conectado
    
    companion object {
        private const val TAG = "WebSocketClient"
        private const val RECONNECT_DELAY = 3000L
        private const val HEARTBEAT_INTERVAL = 15000L
    }
}

// ============================================================================
// MessageHandler
// ============================================================================

object MessageHandler {
    
    private const val TAG = "MessageHandler"
    
    /**
     * Resultado del procesamiento de jocData
     */
    data class JocDataResult(
        var jugador1: String = "",
        var jugador2: String = "",
        var soyJugador1: Boolean = true,
        var soyJugador2: Boolean = false,
        var p1x: Double = 20.0,
        var p1y: Double = 170.0,
        var p1Width: Double = 15.0,
        var p1Height: Double = 100.0,
        var p1Color: String = "RED",
        var p2x: Double = 570.0,
        var p2y: Double = 200.0,
        var p2Width: Double = 15.0,
        var p2Height: Double = 100.0,
        var p2Color: String = "BLACK",
        var ballX: Double = 295.0,
        var ballY: Double = 195.0,
        var ballSize: Double = 22.0,
        var ballColor: String = "WHITE",
        var j1Punts: Int = 0,
        var j2Punts: Int = 0
    )
    
    /**
     * Crear un jugador en el servidor
     */
    fun crearJugador(clientName: String, wsClient: WebSocketClient) {
        val json = JSONObject().apply {
            put("type", "setName")
            put("value", clientName)
        }
        wsClient.sendJSON(json)
        Log.d(TAG, "✅ Enviado setName: $clientName")
    }
    
    /**
     * Enviar mensaje de broadcast
     */
    fun enviarBroadcast(mensaje: String, senderName: String, wsClient: WebSocketClient) {
        val json = JSONObject().apply {
            put("type", "broadcast")
            put("message", mensaje)
            put("senderName", senderName)
        }
        wsClient.sendJSON(json)
        Log.d(TAG, "📢 Enviado broadcast: $mensaje")
    }
    
    /**
     * Enviar movimiento de pala
     */
    fun enviarMovimiento(direction: String, wsClient: WebSocketClient) {
        val json = JSONObject().apply {
            put("type", "move")
            put("direction", direction)
            put("timestamp", System.currentTimeMillis())
        }
        wsClient.sendJSON(json)
        Log.d(TAG, "🎯 Enviado move: $direction")
    }
    
    /**
     * Procesar mensaje recibido del servidor
     */
    fun procesarMensaje(
        mensaje: String,
        onJocData: ((JSONObject) -> Unit)? = null,
        onBroadcastHola: ((String) -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
        onCountdown: ((Int) -> Unit)? = null,
        onGameStart: (() -> Unit)? = null,
        onGameOver: ((String) -> Unit)? = null,
        onPlayerJoined: ((String) -> Unit)? = null,
        onRegistreOk: ((String) -> Unit)? = null
    ) {
        try {
            val json = JSONObject(mensaje)
            val type = json.optString("type", "")
            
            Log.d(TAG, "📩 Mensaje recibido - Type: $type")
            
            when (type) {
                "RegistreOk" -> {
                    val color = json.optString("color", "VERMELL")
                    Log.d(TAG, "✅ RegistreOk - Color asignado: $color")
                    onRegistreOk?.invoke(color)
                }

                "broadcastHola" -> {
                    val value = json.optString("value", "desconegut")
                    Log.d(TAG, "✅ BroadcastHola: $value")
                    onBroadcastHola?.invoke(value)
                }
                
                "jocData" -> {
                    Log.d(TAG, "🎮 JocData recibido")
                    onJocData?.invoke(json)
                }
                
                "error" -> {
                    val errorMsg = json.optString("value", "Error desconegut")
                    Log.e(TAG, "❌ Error del servidor: $errorMsg")
                    onError?.invoke(errorMsg)
                }
                
                "countdown" -> {
                    val value = json.optInt("value", 0)
                    Log.d(TAG, "⏱️ Countdown: $value")
                    onCountdown?.invoke(value)
                }
                
                "gameStart" -> {
                    Log.d(TAG, "🚀 Game Start")
                    onGameStart?.invoke()
                }
                
                "gameOver" -> {
                    val winner = json.optString("winner", "")
                    Log.d(TAG, "🏁 Game Over - Winner: $winner")
                    onGameOver?.invoke(winner)
                }
                
                "playerJoined" -> {
                    val playerName = json.optString("playerName", "")
                    Log.d(TAG, "👤 Player Joined: $playerName")
                    onPlayerJoined?.invoke(playerName)
                }
                
                "broadcast" -> {
                    val broadcastMessage = json.optString("message", "")
                    val senderName = json.optString("senderName", "Jugador")
                    Log.d(TAG, "📢 Broadcast de $senderName: $broadcastMessage")
                    
                    if (broadcastMessage == "hola") {
                        onPlayerJoined?.invoke(senderName)
                    }
                }
                
                else -> {
                    Log.d(TAG, "⚠️ Tipo desconocido: $type - Mensaje: $mensaje")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error procesando mensaje: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Procesar mensaje jocData de forma unificada
     */
    fun procesarJocData(jocData: JSONObject, playerName: String, callback: (JocDataResult) -> Unit) {
        try {
            val result = JocDataResult()
            
            // Extraer jugadores (lista de nombres)
            val jugadors = jocData.optJSONArray("Jugadors")
            val playerNames = mutableListOf<String>()
            if (jugadors != null) {
                for (i in 0 until jugadors.length()) {
                    val nombre = jugadors.optString(i, "")
                    if (nombre.isNotEmpty()) {
                        playerNames.add(nombre)
                    }
                }
            }
            
            // Asignar nombres temporalmente (se corregirá en MainActivity con la info de RegistreOk)
            if (playerNames.isNotEmpty()) result.jugador1 = playerNames[0]
            if (playerNames.size > 1) result.jugador2 = playerNames[1]
            
            // Determinar mi posición
            result.soyJugador1 = (result.jugador1 == playerName)
            result.soyJugador2 = (result.jugador2 == playerName)
            
            // Si no estamos en la lista, asumir que somos jugador 2
            if (!result.soyJugador1 && !result.soyJugador2 && result.jugador1.isEmpty()) {
                result.soyJugador1 = true
                result.jugador1 = playerName
            }
            
            // Extraer objetos del juego
            val objectsList = jocData.optJSONArray("objectsList")
            if (objectsList != null) {
                for (i in 0 until objectsList.length()) {
                    val obj = objectsList.getJSONObject(i)
                    val id = obj.optString("id", "")
                    
                    when {
                        id.equals("P1", ignoreCase = true) -> {
                            result.p1x = obj.optDouble("x", 0.0)
                            result.p1y = obj.optDouble("y", 0.0)
                            result.p1Width = obj.optDouble("ancho", 10.0)
                            result.p1Height = obj.optDouble("alto", 60.0)
                            result.p1Color = obj.optString("color", "RED")
                        }
                        id.equals("P2", ignoreCase = true) -> {
                            result.p2x = obj.optDouble("x", 0.0)
                            result.p2y = obj.optDouble("y", 0.0)
                            result.p2Width = obj.optDouble("ancho", 10.0)
                            result.p2Height = obj.optDouble("alto", 60.0)
                            result.p2Color = obj.optString("color", "BLACK")
                        }
                        id.startsWith("B", ignoreCase = true) -> {
                            result.ballX = obj.optDouble("x", 0.0)
                            result.ballY = obj.optDouble("y", 0.0)
                            result.ballSize = obj.optDouble("ancho", 10.0) // Asumimos que es cuadrada/circular
                            result.ballColor = obj.optString("color", "WHITE")
                        }
                    }
                }
            }
            
            // Puntuaciones
            result.j1Punts = jocData.optInt("J1Punts", 0)
            result.j2Punts = jocData.optInt("J2Punts", 0)
            
            // Intentar obtener puntuaciones de campos alternativos
            if (result.j1Punts == 0) {
                result.j1Punts = jocData.optInt("leftScore", 0)
            }
            if (result.j2Punts == 0) {
                result.j2Punts = jocData.optInt("rightScore", 0)
            }
            
            Log.d(TAG, "🎯 JocData procesado - J1: ${result.jugador1}, J2: ${result.jugador2}, " +
                      "SoyJ1: ${result.soyJugador1}, Puntos: ${result.j1Punts}-${result.j2Punts}")
            
            callback(result)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error procesando jocData: ${e.message}")
            e.printStackTrace()
        }
    }
    
    fun extraerJugadores(jocData: JSONObject): List<ClientData> {
        val jugadores = mutableListOf<ClientData>()
        val jugadoresArray = jocData.optJSONArray("Jugadors")
        
        if (jugadoresArray != null) {
            for (i in 0 until jugadoresArray.length()) {
                val nombre = jugadoresArray.optString(i, "")
                if (nombre.isNotEmpty()) {
                    jugadores.add(ClientData(nombre, "gray"))
                }
            }
        }
        
        Log.d(TAG, "👥 Jugadores extraídos: ${jugadores.size}")
        return jugadores
    }
    
    fun extraerObjetos(jocData: JSONObject, containerWidth: Int, containerHeight: Int): List<GameObject> {
        val objetos = mutableListOf<GameObject>()
        val objetosArray = jocData.optJSONArray("objectsList")
        
        if (objetosArray != null) {
            for (i in 0 until objetosArray.length()) {
                val obj = objetosArray.optJSONObject(i)
                if (obj != null) {
                    objetos.add(GameObject.fromJSON(obj, containerWidth, containerHeight))
                }
            }
        }
        
        Log.d(TAG, "🎯 Objetos extraídos: ${objetos.size}")
        return objetos
    }
    
    fun extraerPuntuaciones(jocData: JSONObject): Pair<Int, Int> {
        val j1Punts = jocData.optInt("J1Punts", 0)
        val j2Punts = jocData.optInt("J2Punts", 0)
        
        Log.d(TAG, "🎯 Puntuaciones - J1: $j1Punts, J2: $j2Punts")
        return Pair(j1Punts, j2Punts)
    }
}

// ============================================================================
// GestioMoviment
// ============================================================================

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
