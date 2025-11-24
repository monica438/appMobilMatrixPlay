package com.example.appMobilMatrixPlay

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

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
        onPlayerJoined: ((String) -> Unit)? = null
    ) {
        try {
            val json = JSONObject(mensaje)
            val type = json.optString("type", "")
            
            Log.d(TAG, "📩 Mensaje recibido - Type: $type")
            
            when (type) {
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
            
            // Extraer jugadores
            val jugadors = jocData.optJSONArray("Jugadors")
            if (jugadors != null) {
                for (i in 0 until jugadors.length()) {
                    val nombre = jugadors.optString(i, "")
                    if (nombre.isNotEmpty()) {
                        when (i) {
                            0 -> result.jugador1 = nombre
                            1 -> result.jugador2 = nombre
                        }
                    }
                }
            }
            
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