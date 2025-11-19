package com.example.appMobilMatrixPlay

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MatrixPlayDebug"
        private var sharedWebSocketClient: WebSocketClient? = null
        
        fun setWebSocketClient(client: WebSocketClient?) {
            sharedWebSocketClient = client
        }
    }

    private lateinit var txtStatus: TextView
    private lateinit var scoreLeft: TextView
    private lateinit var scoreRight: TextView
    private lateinit var playerLeftName: TextView
    private lateinit var playerRightName: TextView
    private lateinit var playerLeftIcon: ImageView
    private lateinit var playerRightIcon: ImageView
    
    private lateinit var gameView: GameView
    
    private var wsClient: WebSocketClient? = null
    private var playerName: String = ""
    private var myColor: String = "RED"
    private var myServerColor: String = "VERMELL"  // Color para enviar al servidor (VERMELL o NEGRE)
    private var isLeftPlayer: Boolean = true
    
    private var leftScore: Int = 0
    private var rightScore: Int = 0
    
    private lateinit var gestioMoviment: GestioMoviment

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        getIntentData()
        setupGameView()
        connectToServer()
    }
    
    private fun initViews() {
        txtStatus = findViewById(R.id.txt_status)
        scoreLeft = findViewById(R.id.score_left)
        scoreRight = findViewById(R.id.score_right)
        playerLeftName = findViewById(R.id.player_left_name)
        playerRightName = findViewById(R.id.player_right_name)
        playerLeftIcon = findViewById(R.id.player_left_icon)
        playerRightIcon = findViewById(R.id.player_right_icon)
        gameView = findViewById(R.id.game_view)
        
        gameView.setBallColor(android.graphics.Color.WHITE)
    }
    
    private fun getIntentData() {
        val protocol = intent.getStringExtra("protocol") ?: "wss"
        val host = intent.getStringExtra("host") ?: "matrixplay4.ieti.site"
        val port = intent.getStringExtra("port") ?: "443"
        playerName = intent.getStringExtra("playerName") ?: "Jugador"
        val player2Name = intent.getStringExtra("player2Name") ?: "Esperant..."
        val colorFromIntent = intent.getStringExtra("myColor") ?: "rojo"
        
        myColor = when (colorFromIntent.lowercase()) {
            "rojo" -> "RED"
            "negro" -> "BLACK"
            else -> colorFromIntent.uppercase()
        }
        
        isLeftPlayer = (myColor == "RED")
        myServerColor = if (isLeftPlayer) "VERMELL" else "NEGRE"
        
        Log.d(TAG, "🎮 Configuración - Color: $myColor, ServerColor: $myServerColor, isLeftPlayer: $isLeftPlayer, Player: $playerName")
        
        if (isLeftPlayer) {
            playerLeftName.text = playerName
            playerRightName.text = player2Name
            playerLeftIcon.setImageResource(R.drawable.rojo)
            playerRightIcon.setImageResource(R.drawable.negro)
        } else {
            playerRightName.text = playerName
            playerLeftName.text = player2Name
            playerRightIcon.setImageResource(R.drawable.negro)
            playerLeftIcon.setImageResource(R.drawable.rojo)
        }
    }
    
    private fun setupGameView() {
        gameView.isLeftPlayer = isLeftPlayer
        gameView.localIsLeftPlayer = isLeftPlayer
        gameView.showSlider = true
        
        if (isLeftPlayer) {
            gameView.leftPaddleY = 0.5f
        } else {
            gameView.rightPaddleY = 0.5f
        }
        
        gameView.onPaddlePositionChanged = { normalizedY ->
            if (::gestioMoviment.isInitialized) {
                gestioMoviment.handleTouchMove(normalizedY)
            }
        }
        
        gameView.onTouchReleased = {
            if (::gestioMoviment.isInitialized) {
                gestioMoviment.stopMovement()
            }
        }
    }

    private fun connectToServer() {
        val reuseConnection = intent.getBooleanExtra("reuseConnection", false)
        
        if (reuseConnection && sharedWebSocketClient != null) {
            // Reutilizar conexión existente
            Log.d(TAG, "♻️ Reutilizando conexión WebSocket existente")
            Log.d(TAG, "📊 Estado de conexión compartida: ${if (sharedWebSocketClient?.isConnected() == true) "CONECTADA" else "DESCONECTADA"}")
            wsClient = sharedWebSocketClient
            sharedWebSocketClient = null // Limpiar para evitar fugas de memoria
            
            // Configurar handlers de mensajes
            wsClient?.onMessage { message ->
                handleServerMessage(message)
            }
            
            wsClient?.onError { error ->
                runOnUiThread {
                    txtStatus.text = "Error: $error"
                    Toast.makeText(this, "❌ Error: $error", Toast.LENGTH_LONG).show()
                }
            }
            
            wsClient?.onClose {
                runOnUiThread {
                    txtStatus.text = "Desconnectat"
                    Toast.makeText(this, "🔴 Desconnectat del servidor", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Verificar si la conexión sigue activa
            if (wsClient?.isConnected() != true) {
                Log.e(TAG, "⚠️ Conexión WebSocket no está activa, intentando reconectar...")
                txtStatus.text = "Reconnectant..."
                wsClient?.connect()
            }
            
            // Inicializar GestioMoviment con la conexión existente
            runOnUiThread {
                txtStatus.text = "Connectat - En joc"
                wsClient?.let { ws ->
                    gestioMoviment = GestioMoviment(ws)
                    Log.d(TAG, "✅ GestioMoviment inicializado con conexión existente")
                }
            }
        } else {
            // Crear nueva conexión (modo legacy)
            val protocol = intent.getStringExtra("protocol") ?: "ws"
            val host = intent.getStringExtra("host") ?: "192.168.21.102"
            val port = intent.getStringExtra("port") ?: "3000"
            val url = "$protocol://$host:$port"
            
            txtStatus.text = "Connectant..."
            Log.d(TAG, "🔗 Conectando a: $url")
            
            wsClient = WebSocketClient(url)
            
            wsClient?.onOpen {
                runOnUiThread {
                    txtStatus.text = "Connectat - Registrant..."
                    Toast.makeText(this, "✅ Connectat al servidor", Toast.LENGTH_SHORT).show()
                    
                    wsClient?.let { ws ->
                        gestioMoviment = GestioMoviment(ws)
                        MessageHandler.crearJugador(playerName, ws)
                    }
                }
            }
            
            wsClient?.onMessage { message ->
                handleServerMessage(message)
            }
            
            wsClient?.onError { error ->
                runOnUiThread {
                    txtStatus.text = "Error: $error"
                    Toast.makeText(this, "❌ Error: $error", Toast.LENGTH_LONG).show()
                }
            }
            
            wsClient?.onClose {
                runOnUiThread {
                    txtStatus.text = "Desconnectat"
                    Toast.makeText(this, "🔴 Desconnectat del servidor", Toast.LENGTH_SHORT).show()
                }
            }
            
            wsClient?.connect()
        }
    }
    
    private fun handleServerMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.optString("type", "")
            
            Log.d(TAG, "📩 Mensaje - Type: $type")
            
            when (type) {
                "broadcastHola" -> {
                    val value = json.optString("value", "")
                    runOnUiThread {
                        txtStatus.text = value
                        Log.d(TAG, "✅ BroadcastHola: $value")
                    }
                }
                
                "jocData" -> {
                    MessageHandler.procesarJocData(json, playerName) { result ->
                        runOnUiThread {
                            updateGameState(result)
                        }
                    }
                }
                
                "gameStart" -> {
                    runOnUiThread {
                        txtStatus.text = "¡Juego iniciado!"
                        Toast.makeText(this, "🚀 ¡Comienza el juego!", Toast.LENGTH_SHORT).show()
                    }
                }
                
                "score" -> {
                    val leftScore = json.optInt("leftScore", 0)
                    val rightScore = json.optInt("rightScore", 0)
                    runOnUiThread {
                        scoreLeft.text = leftScore.toString()
                        scoreRight.text = rightScore.toString()
                        Log.d(TAG, "📊 Score actualizado: $leftScore - $rightScore")
                    }
                }
                
                "gameOver" -> {
                    val winner = json.optString("winner", "")
                    runOnUiThread {
                        showGameOver(winner)
                    }
                }
                
                "playerJoined" -> {
                    val joinedPlayer = json.optString("playerName", "")
                    runOnUiThread {
                        if (isLeftPlayer) {
                            playerRightName.text = joinedPlayer
                        } else {
                            playerLeftName.text = joinedPlayer
                        }
                        Toast.makeText(this, "👤 $joinedPlayer se unió", Toast.LENGTH_SHORT).show()
                    }
                }
                
                "error" -> {
                    val errorMsg = json.optString("value", "Error desconocido")
                    runOnUiThread {
                        Toast.makeText(this, "❌ Error: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                }
                
                "countdown" -> {
                    val value = json.optInt("value", 0)
                    runOnUiThread {
                        if (value > 0) {
                            txtStatus.text = "Començant en $value..."
                        } else {
                            txtStatus.text = "GO!"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error procesando mensaje: ${e.message}")
        }
    }
    
    private fun updateGameState(result: MessageHandler.JocDataResult) {
        Log.d(TAG, "📥 updateGameState llamado - J1: ${result.jugador1}, J2: ${result.jugador2}")
        
        // Actualizar nombres - solo si el servidor envía un nombre válido
        if (result.jugador1.isNotEmpty()) {
            playerLeftName.text = result.jugador1
        }
        if (result.jugador2.isNotEmpty()) {
            playerRightName.text = result.jugador2
        }
        
        // Actualizar puntuaciones
        scoreLeft.text = result.j1Punts.toString()
        scoreRight.text = result.j2Punts.toString()
        
        // Determinar mi posición actual y actualizar colores
        val nuevaPosicionIzquierda = result.soyJugador1

        // Determinar el color que el servidor asigna a cada pala
        val p1Color = result.p1Color.uppercase()
        val p2Color = result.p2Color.uppercase()

        // Color mío según el servidor
        val myColorFromServer = if (result.soyJugador1) p1Color else p2Color

        // Actualizar estado local si cambió
        if (isLeftPlayer != nuevaPosicionIzquierda || myColor != myColorFromServer) {
            isLeftPlayer = nuevaPosicionIzquierda
            myColor = myColorFromServer
            // Indicar en la vista cuál es la pala local (la que tenga mi color)
            val localIsLeft = p1Color.equals(myColor, ignoreCase = true)
            gameView.isLeftPlayer = isLeftPlayer
            gameView.localIsLeftPlayer = localIsLeft
            Log.d(TAG, "🔄 Actualización: isLeftPlayer=$isLeftPlayer, myColor=$myColor, localIsLeft=$localIsLeft (P1=$p1Color, P2=$p2Color)")
        }
        
        // Actualizar iconos de jugadores
        updatePlayerIcons()
        
        // Actualizar bola
        updateBallColor(result.ballColor)
        val normalizedBallX = (result.ballX / 600.0).toFloat().coerceIn(0f, 1f)
        val normalizedBallY = (result.ballY / 400.0).toFloat().coerceIn(0f, 1f)
        updateBallPosition(normalizedBallX, normalizedBallY)
        
        // Actualizar palas usando coordenadas del servidor (0..400)
        Log.d(TAG, "🎮 Actualizando palas - P1Y raw: ${result.p1y} | P2Y raw: ${result.p2y}")

        // Para logs y comparaciones fáciles, calcular también la normalizada simple (0..1)
        val normalizedP1Y = (result.p1y / 400.0).toFloat().coerceIn(0f, 1f)
        val normalizedP2Y = (result.p2y / 400.0).toFloat().coerceIn(0f, 1f)

        if (isLeftPlayer) {
            // Soy jugador izquierdo (P1): actualizar la pala del RIVAL (derecha) desde la coordenada del servidor
            gameView.updateRightPaddleFromServer(result.p2y.toInt())
            Log.d(TAG, "👤 Mi pala (P1) local: ${gameView.leftPaddleY}")
        } else {
            // Soy jugador derecho (P2): actualizar la pala del RIVAL (izquierda) desde la coordenada del servidor
            gameView.updateLeftPaddleFromServer(result.p1y.toInt())
            Log.d(TAG, "👤 Mi pala (P2) local: ${gameView.rightPaddleY}")
        }
        
        Log.d(TAG, "🎯 Estado actualizado - Bola: (${"%1.2f".format(normalizedBallX)}, ${"%1.2f".format(normalizedBallY)}), " +
                  "P1: ${"%1.2f".format(normalizedP1Y)}, P2: ${"%1.2f".format(normalizedP2Y)}")
    }
    
    private fun updatePlayerIcons() {
        if (isLeftPlayer) {
            playerLeftIcon.setImageResource(R.drawable.rojo)
            playerRightIcon.setImageResource(R.drawable.negro)
        } else {
            playerRightIcon.setImageResource(R.drawable.negro)
            playerLeftIcon.setImageResource(R.drawable.rojo)
        }
    }
    
    private fun updateBallPosition(normalizedX: Float, normalizedY: Float) {
        gameView.updateBallPosition(normalizedX, normalizedY)
    }
    
    private fun updateBallColor(color: String) {
        val ballColor = when (color.uppercase()) {
            "WHITE" -> android.graphics.Color.WHITE
            "RED" -> android.graphics.Color.RED
            "BLACK" -> android.graphics.Color.BLACK
            "YELLOW" -> android.graphics.Color.YELLOW
            "BLUE" -> android.graphics.Color.BLUE
            "GREEN" -> android.graphics.Color.GREEN
            else -> android.graphics.Color.WHITE
        }
        gameView.setBallColor(ballColor)
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                movePaddleUp()
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                movePaddleDown()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
    
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (::gestioMoviment.isInitialized) {
                    gestioMoviment.handleKeyEvent(isPressed = false, isUp = true)
                }
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (::gestioMoviment.isInitialized) {
                    gestioMoviment.handleKeyEvent(isPressed = false, isUp = false)
                }
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }
    
    private fun movePaddleUp() {
        if (::gestioMoviment.isInitialized) {
            gestioMoviment.handleKeyEvent(isPressed = true, isUp = true)
        }
    }
    
    private fun movePaddleDown() {
        if (::gestioMoviment.isInitialized) {
            gestioMoviment.handleKeyEvent(isPressed = true, isUp = false)
        }
    }

    private fun showGameOver(winner: String) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setCancelable(false)
        
        val didIWin = winner == playerName || 
                      (winner.uppercase() == "RED" && isLeftPlayer) ||
                      (winner == "negro" && !isLeftPlayer)
        
        val message = if (didIWin) {
            "🎉 ¡FELICIDADES! 🎉\n\n¡Has ganado la partida!"
        } else {
            "😔 Has perdido\n\nEl otro jugador ha ganado"
        }
        
        builder.setTitle(if (didIWin) "¡VICTORIA!" else "Derrota")
        builder.setMessage(message)
        builder.setPositiveButton("Volver al Menú") { _, _ ->
            wsClient?.disconnect()
            finish()
        }
        
        builder.create().show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        wsClient?.disconnect()
        Log.d(TAG, "🔴 MainActivity destroyed")
    }
}