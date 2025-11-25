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
                "RegistreOk" -> {
                    val color = json.optString("color", "VERMELL")
                    Log.d(TAG, "✅ RegistreOk - Color asignado: $color")
                    
                    // Asignar lado basado en el color recibido del servidor
                    // VERMELL -> P1 (Izquierda)
                    // NEGRE -> P2 (Derecha)
                    val isLeft = color.equals("VERMELL", ignoreCase = true)
                    
                    runOnUiThread {
                        isLeftPlayer = isLeft
                        myColor = if (isLeft) "RED" else "BLACK"
                        myServerColor = color
                        
                        gameView.isLeftPlayer = isLeftPlayer
                        gameView.localIsLeftPlayer = isLeftPlayer
                        
                        // Actualizar iconos inmediatamente
                        updatePlayerIcons()
                        
                        Log.d(TAG, "👤 Identidad confirmada: ${if(isLeft) "P1 (Izquierda/Rojo)" else "P2 (Derecha/Negro)"}")
                    }
                }

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
                
                "gameOver" -> {
                    val winner = json.optString("winner", "")
                    val loser = json.optString("loser", "")
                    
                    Log.d(TAG, "🏁 GameOver recibido - Winner: $winner, Loser: $loser")
                    
                    runOnUiThread {
                        showGameOver(winner)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error procesando mensaje: ${e.message}")
        }
    }
    
    private fun updateGameState(result: MessageHandler.JocDataResult) {
        Log.d(TAG, "📥 updateGameState llamado - J1: ${result.jugador1}, J2: ${result.jugador2}")
        
        // Lógica robusta para asignar nombres basada en mi identidad confirmada (isLeftPlayer)
        // El servidor envía una lista de nombres en 'Jugadors' sin orden garantizado.
        // Debemos encontrar cuál es el mío y cuál es el del rival.
        
        var myName = playerName
        var opponentName = "Esperant..."
        
        // Buscar nombre del oponente en la lista recibida
        // Si result.jugador1 o result.jugador2 contienen nombres, usarlos para deducir
        val names = mutableListOf<String>()
        if (result.jugador1.isNotEmpty()) names.add(result.jugador1)
        if (result.jugador2.isNotEmpty()) names.add(result.jugador2)
        
        for (name in names) {
            if (name != playerName) {
                opponentName = name
                break
            }
        }
        
        // Asignar nombres a los TextViews según mi lado
        if (isLeftPlayer) {
            // Soy P1 (Izquierda)
            playerLeftName.text = myName
            playerRightName.text = opponentName
        } else {
            // Soy P2 (Derecha)
            playerRightName.text = myName
            playerLeftName.text = opponentName
        }
        
        // Actualizar puntuaciones
        // J1Punts siempre es P1 (Izquierda/Rojo)
        // J2Punts siempre es P2 (Derecha/Negro)
        scoreLeft.text = result.j1Punts.toString()
        scoreRight.text = result.j2Punts.toString()
        
        // Guardar puntuaciones locales para lógica de GameOver
        leftScore = result.j1Punts
        rightScore = result.j2Punts
        
        // Determinar el color que el servidor asigna a cada pala
        val p1Color = result.p1Color.ifEmpty { "RED" }.uppercase()
        val p2Color = result.p2Color.ifEmpty { "BLACK" }.uppercase()

        // Actualizar colores en la vista
        val p1ColorInt = parseColor(p1Color)
        val p2ColorInt = parseColor(p2Color)
        gameView.setPaddleColors(p1ColorInt, p2ColorInt)

        // Actualizar iconos de jugadores
        updatePlayerIcons()
        
        // Actualizar bola
        updateBallColor(result.ballColor)
        val normalizedBallX = (result.ballX / 600.0).toFloat().coerceIn(0f, 1f)
        val normalizedBallY = (result.ballY / 400.0).toFloat().coerceIn(0f, 1f)
        updateBallPosition(normalizedBallX, normalizedBallY)
        
        // Actualizar dimensiones de las palas si vienen del servidor
        if (result.p1Height > 0) {
            gameView.serverPaddleHeight = result.p1Height.toFloat()
            gameView.serverPaddleWidth = result.p1Width.toFloat()
            
            // Actualizar posiciones X normalizadas
            gameView.leftPaddleX = (result.p1x / 600.0).toFloat()
            gameView.rightPaddleX = (result.p2x / 600.0).toFloat()
        }
        
        // Actualizar tamaño de la bola si viene del servidor
        if (result.ballSize > 0) {
            gameView.serverBallSize = result.ballSize.toFloat()
        }

        // Actualizar posiciones de las palas
        // P1 es siempre Izquierda, P2 es siempre Derecha en la lógica del servidor
        if (isLeftPlayer) {
            // Soy jugador izquierdo (P1): actualizar la pala del RIVAL (derecha/P2) desde la coordenada del servidor
            gameView.updateRightPaddleFromServer(result.p2y.toInt())
        } else {
            // Soy jugador derecho (P2): actualizar la pala del RIVAL (izquierda/P1) desde la coordenada del servidor
            gameView.updateLeftPaddleFromServer(result.p1y.toInt())
        }
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
        val ballColor = parseColor(color)
        gameView.setBallColor(ballColor)
    }

    private fun parseColor(colorName: String): Int {
        return when (colorName.uppercase()) {
            "RED", "VERMELL", "ROJO" -> android.graphics.Color.RED
            "BLACK", "NEGRE", "NEGRO" -> android.graphics.Color.BLACK
            "BLUE", "BLAU", "AZUL" -> android.graphics.Color.BLUE
            "GREEN", "VERD", "VERDE" -> android.graphics.Color.GREEN
            "WHITE", "BLANC", "BLANCO" -> android.graphics.Color.WHITE
            "YELLOW", "GROC", "AMARILLO" -> android.graphics.Color.YELLOW
            else -> android.graphics.Color.GRAY
        }
    }
    
    private fun showGameOver(winner: String) {
        // Determinar si gané basado en puntuaciones (más fiable)
        val myScore = if (isLeftPlayer) leftScore else rightScore
        val otherScore = if (isLeftPlayer) rightScore else leftScore
        
        val didIWin = if (myScore != otherScore) {
            myScore > otherScore
        } else {
            // Fallback: comprobar por nombre/color si hay empate o desconexión
            winner == playerName || 
            (winner.equals("RED", ignoreCase = true) && isLeftPlayer) ||
            (winner.equals("BLACK", ignoreCase = true) && !isLeftPlayer) ||
            (winner.equals("VERMELL", ignoreCase = true) && isLeftPlayer) ||
            (winner.equals("NEGRE", ignoreCase = true) && !isLeftPlayer)
        }
        
        Log.d(TAG, "🏁 GameOver - Winner: $winner, MyScore: $myScore, OtherScore: $otherScore, DidIWin: $didIWin")
        
        // Desconectar del servidor
        wsClient?.disconnect()
        
        // Abrir actividad de Game Over
        val intent = android.content.Intent(this, GameOverActivity::class.java)
        intent.putExtra("didWin", didIWin)
        startActivity(intent)
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        wsClient?.disconnect()
        Log.d(TAG, "🔴 MainActivity destroyed")
    }
}