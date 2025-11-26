package com.example.appMobilMatrixPlay

import android.os.Handler
import android.os.Looper
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
                handler.post {
                    startHeartbeat()
                    onOpenCallback?.invoke()
                }
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                handler.post {
                    onMessageCallback?.invoke(text)
                }
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                conectado = false
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                conectado = false
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
                connect()
            }
        }, RECONNECT_DELAY)
    }
    
    fun send(message: String) {
        if (conectado) {
            webSocket?.send(message)
        }
    }
    
    fun sendJSON(json: JSONObject) {
        send(json.toString())
    }
    
    fun disconnect() {
        shouldReconnect = false
        conectado = false
        stopHeartbeat()
        webSocket?.close(1000, "Client closing")
        webSocket = null
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
        private const val RECONNECT_DELAY = 3000L
        private const val HEARTBEAT_INTERVAL = 15000L
    }
}

// ============================================================================
// MessageHandler
// ============================================================================

object MessageHandler {
    
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
            
            when (type) {
                "RegistreOk" -> {
                    val color = json.optString("color", "VERMELL")
                    onRegistreOk?.invoke(color)
                }

                "broadcastHola" -> {
                    val value = json.optString("value", "desconegut")
                    onBroadcastHola?.invoke(value)
                }
                
                "jocData" -> {
                    onJocData?.invoke(json)
                }
                
                "error" -> {
                    val errorMsg = json.optString("value", "Error desconegut")
                    onError?.invoke(errorMsg)
                }
                
                "countdown" -> {
                    val value = json.optInt("value", 0)
                    onCountdown?.invoke(value)
                }
                
                "gameStart" -> {
                    onGameStart?.invoke()
                }
                
                "gameOver" -> {
                    val winner = json.optString("winner", "")
                    onGameOver?.invoke(winner)
                }
                
                "playerJoined" -> {
                    val playerName = json.optString("playerName", "")
                    onPlayerJoined?.invoke(playerName)
                }
                
                "broadcast" -> {
                    val broadcastMessage = json.optString("message", "")
                    val senderName = json.optString("senderName", "Jugador")
                    
                    if (broadcastMessage == "hola") {
                        onPlayerJoined?.invoke(senderName)
                    }
                }
            }
        } catch (e: Exception) {
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
            
            callback(result)
            
        } catch (e: Exception) {
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
        
        return objetos
    }
    
    fun extraerPuntuaciones(jocData: JSONObject): Pair<Int, Int> {
        val j1Punts = jocData.optInt("J1Punts", 0)
        val j2Punts = jocData.optInt("J2Punts", 0)
        
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
        private const val SEND_INTERVAL = 8L
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
        enviarPosicion(lastY.toInt())
    }
    
    fun getDireccionActual(): String = direccioActual
}
