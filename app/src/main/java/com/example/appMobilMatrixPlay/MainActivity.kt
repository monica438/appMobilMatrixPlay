package com.example.appMobilMatrixPlay

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView
    private lateinit var scoreLeft: TextView
    private lateinit var scoreRight: TextView
    private lateinit var playerLeftName: TextView
    private lateinit var playerRightName: TextView
    private lateinit var playerLeftIcon: ImageView
    private lateinit var playerRightIcon: ImageView
    
    private lateinit var gameContainer: View
    private lateinit var paddleLeft: View
    private lateinit var paddleRight: View
    private lateinit var ball: View
    
    // Variables del websocket
    private var wsClient: WebSocketClient? = null
    private var playerName: String = ""
    private var myColor: String = "rojo"
    private var isLeftPlayer: Boolean = true
    
    private var leftScore: Int = 0
    private var rightScore: Int = 0

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar vistas
        txtStatus = findViewById(R.id.txt_status)
        scoreLeft = findViewById(R.id.score_left)
        scoreRight = findViewById(R.id.score_right)
        playerLeftName = findViewById(R.id.player_left_name)
        playerRightName = findViewById(R.id.player_right_name)
        playerLeftIcon = findViewById(R.id.player_left_icon)
        playerRightIcon = findViewById(R.id.player_right_icon)
        
        gameContainer = findViewById(R.id.game_container)
        paddleLeft = findViewById(R.id.paddle_left)
        paddleRight = findViewById(R.id.paddle_right)
        ball = findViewById(R.id.ball)
        
        // Inicializar la bola como blanca
        updateBallColor("WHITE")

        // Obtener config del servidor
        val protocol = intent.getStringExtra("protocol") ?: "wss"
        val host = intent.getStringExtra("host") ?: "matrixplay4.ieti.site"
        val port = intent.getStringExtra("port") ?: "443"
        playerName = intent.getStringExtra("playerName") ?: "Jugador"
        val player2Name = intent.getStringExtra("player2Name") ?: "Esperando..."
        myColor = intent.getStringExtra("myColor") ?: "rojo"
        
        // Determinar si somos el jugador izquierdo o derecho
        isLeftPlayer = (myColor == "rojo")
        
        // Configurar nombres e iconos
        if (isLeftPlayer) {
            playerLeftName.text = playerName
            playerRightName.text = player2Name
        } else {
            playerRightName.text = playerName
            playerLeftName.text = player2Name
        }

        // Conectar al servidor
        connectToServer("$protocol://$host:$port")
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
    
    private fun movePaddleUp() {
        val paddle = if (isLeftPlayer) paddleLeft else paddleRight
        val containerHeight = gameContainer.height
        val paddleHeight = paddle.height
        
        // Mover hacia arriba (incremento de 20dp)
        val step = 20 * resources.displayMetrics.density
        var newY = paddle.y - step
        
        // Limitar movimiento dentro del contenedor
        if (newY < 0) newY = 0f
        
        paddle.y = newY
        sendPaddlePosition(newY + paddleHeight / 2)
    }
    
    private fun movePaddleDown() {
        val paddle = if (isLeftPlayer) paddleLeft else paddleRight
        val containerHeight = gameContainer.height
        val paddleHeight = paddle.height
        
        // Mover hacia abajo (incremento de 20dp)
        val step = 20 * resources.displayMetrics.density
        var newY = paddle.y + step
        
        // Limitar movimiento dentro del contenedor
        if (newY > containerHeight - paddleHeight) newY = (containerHeight - paddleHeight).toFloat()
        
        paddle.y = newY
        sendPaddlePosition(newY + paddleHeight / 2)
    }

    private fun movePaddle(y: Float) {
        val paddle = if (isLeftPlayer) paddleLeft else paddleRight
        val containerHeight = gameContainer.height
        val paddleHeight = paddle.height
        
        // Calcular nueva posición Y (centrada en el toque)
        var newY = y - (paddleHeight / 2)
        
        // Limitar movimiento dentro del contenedor
        if (newY < 0) newY = 0f
        if (newY > containerHeight - paddleHeight) newY = (containerHeight - paddleHeight).toFloat()
        
        paddle.y = newY
    }
    
    private fun sendPaddlePosition(y: Float) {
        val containerHeight = gameContainer.height
        // Normalizar posición (0.0 a 1.0)
        val normalizedY = (y / containerHeight).toDouble()
        
        val json = JSONObject().apply {
            put("type", "paddleMove")
            put("player", myColor)
            put("position", normalizedY)
        }
        wsClient?.sendJSON(json)
    }

    private fun connectToServer(url: String) {
        txtStatus.text = "Conectando..."
        
        wsClient = WebSocketClient(url)
        
        wsClient?.onOpen {
            runOnUiThread {
                txtStatus.text = "Conectado - Esperando inicio del juego..."
                Toast.makeText(this, "Conectado al servidor", Toast.LENGTH_SHORT).show()
            }
        }
        
        wsClient?.onMessage { message ->
            handleServerMessage(message)
        }
        
        wsClient?.onError { error ->
            runOnUiThread {
                txtStatus.text = "Error de conexión: $error"
                Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
            }
        }
        
        wsClient?.onClose {
            runOnUiThread {
                txtStatus.text = "Desconectado del servidor"
            }
        }
        
        wsClient?.connect()
    }
    
    private fun handleServerMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.optString("type", "")
            
            when (type) {
                "jocData" -> {
                    // Datos del juego - extraer información de la bola
                    val objectsList = json.optJSONArray("objectsList")
                    
                    if (objectsList != null) {
                        // Buscar el objeto de la bola (id="B0")
                        for (i in 0 until objectsList.length()) {
                            val obj = objectsList.getJSONObject(i)
                            val id = obj.optString("id", "")
                            
                            if (id.startsWith("B")) {
                                // Es la bola
                                val ballColor = obj.optString("color", "WHITE")
                                val ballX = obj.optInt("x", 4)
                                val ballY = obj.optInt("y", 4)
                                
                                runOnUiThread {
                                    updateBallColor(ballColor)
                                    // Normalizar posición de la bola (0-7 a 0.0-1.0)
                                    val normalizedX = ballX / 7f
                                    val normalizedY = ballY / 7f
                                    updateBallPosition(normalizedX, normalizedY)
                                }
                            }
                        }
                    }
                }
                
                "gameStart" -> {
                    runOnUiThread {
                        txtStatus.text = "¡Juego iniciado!"
                        Toast.makeText(this, "¡Empieza el Ping Pong!", Toast.LENGTH_SHORT).show()
                    }
                }
                
                "gameData", "gameUpdate" -> {
                    // Actualizar posiciones de pelota y palas
                    val ballX = json.optDouble("ballX", 0.5)
                    val ballY = json.optDouble("ballY", 0.5)
                    val leftPaddleY = json.optDouble("leftPaddleY", 0.5)
                    val rightPaddleY = json.optDouble("rightPaddleY", 0.5)
                    
                    runOnUiThread {
                        updateBallPosition(ballX.toFloat(), ballY.toFloat())
                        if (!isLeftPlayer) {
                            updateOpponentPaddle(leftPaddleY.toFloat(), true)
                        } else {
                            updateOpponentPaddle(rightPaddleY.toFloat(), false)
                        }
                    }
                }
                
                "score" -> {
                    leftScore = json.optInt("leftScore", 0)
                    rightScore = json.optInt("rightScore", 0)
                    runOnUiThread {
                        scoreLeft.text = leftScore.toString()
                        scoreRight.text = rightScore.toString()
                    }
                }
                
                "paddleMove" -> {
                    val player = json.optString("player", "")
                    val position = json.optDouble("position", 0.5).toFloat()
                    
                    // Actualizar pala del oponente
                    if ((player == "rojo" && !isLeftPlayer) || (player == "negro" && isLeftPlayer)) {
                        runOnUiThread {
                            updateOpponentPaddle(position, player == "rojo")
                        }
                    }
                }
                
                "playerJoined" -> {
                    val joinedPlayer = json.optString("playerName", "Jugador 2")
                    runOnUiThread {
                        if (isLeftPlayer) {
                            playerRightName.text = joinedPlayer
                        } else {
                            playerLeftName.text = joinedPlayer
                        }
                        Toast.makeText(this, "$joinedPlayer se ha unido", Toast.LENGTH_SHORT).show()
                    }
                }
                
                "gameOver" -> {
                    val winner = json.optString("winner", "")
                    runOnUiThread {
                        txtStatus.text = "Juego terminado"
                        showGameOver(winner)
                    }
                }
                
                "broadcast" -> {
                    val broadcastMessage = json.optString("message", "")
                    val senderName = json.optString("senderName", "Jugador")
                    if (broadcastMessage != "hola") {
                        runOnUiThread {
                            Toast.makeText(this, "$senderName: $broadcastMessage", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun updateBallPosition(normalizedX: Float, normalizedY: Float) {
        val containerWidth = gameContainer.width
        val containerHeight = gameContainer.height
        
        val ballSize = ball.width
        val x = (normalizedX * containerWidth) - (ballSize / 2)
        val y = (normalizedY * containerHeight) - (ballSize / 2)
        
        ball.x = x
        ball.y = y
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
        
        // Crear un drawable circular con el color
        val drawable = android.graphics.drawable.GradientDrawable()
        drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
        drawable.setColor(ballColor)
        ball.background = drawable
    }
    
    private fun updateOpponentPaddle(normalizedY: Float, isLeft: Boolean) {
        val paddle = if (isLeft) paddleLeft else paddleRight
        val containerHeight = gameContainer.height
        val paddleHeight = paddle.height
        
        val y = (normalizedY * containerHeight) - (paddleHeight / 2)
        paddle.y = y.coerceIn(0f, (containerHeight - paddleHeight).toFloat())
    }

    private fun showGameOver(winner: String) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setCancelable(false)
        
        val didIWin = winner == playerName || 
                      (winner == "rojo" && isLeftPlayer) ||
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
    }
}
