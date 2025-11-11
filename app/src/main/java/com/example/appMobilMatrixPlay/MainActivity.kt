package com.example.appMobilMatrixPlay

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private val ROWS = 6
    private val COLS = 7

    // 0 = vacia, 1 = rojo, 2 = amarillo
    private val board = Array(ROWS) { IntArray(COLS) { 0 } }
    private val cells = Array(ROWS) { arrayOfNulls<ImageView>(COLS) }

    private var currentPlayer = 1 // 1 rojo, 2 amarillo

    private lateinit var table: TableLayout
    private lateinit var colButtons: List<Button>
    private lateinit var turnText: TextView
    
    // variables del websocket
    private var wsClient: WebSocketClient? = null
    private var clientName: String = ""
    private var clients: List<ClientData> = emptyList()
    private var pieces: List<GameObject> = emptyList()
    private var isMyTurn: Boolean = false
    private var myRole: String? = null
    private var serverHost: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        table = findViewById(R.id.board_table)
        turnText = findViewById(R.id.turnText)

        // los 7 botones d las columnas
        colButtons = listOf(
            findViewById(R.id.btn_col_0),
            findViewById(R.id.btn_col_1),
            findViewById(R.id.btn_col_2),
            findViewById(R.id.btn_col_3),
            findViewById(R.id.btn_col_4),
            findViewById(R.id.btn_col_5),
            findViewById(R.id.btn_col_6)
        )

        createBoard()
        setupButtons()
        
        // obtener config del server
    val protocol = intent.getStringExtra("protocol") ?: "ws"
    val host = intent.getStringExtra("host") ?: "10.0.2.2"
    val port = intent.getStringExtra("port") ?: "3000"
    serverHost = host

    connectToServer("$protocol://$host:$port")
    }

    private fun createBoard() {
        val density = resources.displayMetrics.density
        val sizePx = (48 * density).toInt()
        val marginPx = (4 * density).toInt()

        // iteramos cada fila
        for (r in 0 until ROWS) {
            val row = TableRow(this)
            row.layoutParams = TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT
            )
            row.gravity = Gravity.CENTER

            // añadimos las celdas
            for (c in 0 until COLS) {
                val img = ImageView(this)
                val lp = TableRow.LayoutParams(sizePx, sizePx)
                lp.setMargins(marginPx, marginPx, marginPx, marginPx)
                img.layoutParams = lp
                img.setImageResource(R.drawable.circle_empty) // circulo vacio
                img.scaleType = ImageView.ScaleType.CENTER_INSIDE
                row.addView(img)

                cells[r][c] = img
                board[r][c] = 0
            }

            table.addView(row)
        }
    }


    // poner los botones para poner las fichas
    private fun setupButtons() {
        for (c in 0 until COLS) {
            colButtons[c].setOnClickListener { 
                if (isMyTurn) {
                    sendPlay(c)
                } else {
                    Toast.makeText(this, "No es tu turno", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Conectarse al server
    private fun connectToServer(url: String) {
        wsClient = WebSocketClient(url)
        
        wsClient?.onOpen {
            runOnUiThread {
                Toast.makeText(this, "Conectado al servidor", Toast.LENGTH_SHORT).show()
                // Enviar "hola" en broadcast solo si estamos conectados al servidor Proxmox
                if (serverHost == "matrixplay4.ieticloudpro.ieti.cat") {
                    sendBroadcastMessage("hola")
                }
            }
        }
        
        wsClient?.onMessage { message ->
            handleServerMessage(message)
        }
        
        wsClient?.onError { error ->
            runOnUiThread {
                Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
            }
        }
        
        wsClient?.onClose {
            runOnUiThread {
                Toast.makeText(this, "Desconectado del servidor", Toast.LENGTH_SHORT).show()
            }
        }
        
        wsClient?.connect()
    }
    
    private fun handleServerMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.getString("type")
            
            when (type) {
                "serverData" -> {
                    clientName = json.getString("clientName")
                    
                    // parsear clientes
                    val clientsArray = json.getJSONArray("clientsList")
                    val clientsList = mutableListOf<ClientData>()
                    for (i in 0 until clientsArray.length()) {
                        clientsList.add(ClientData.fromJSON(clientsArray.getJSONObject(i)))
                    }
                    clients = clientsList
                    
                    // si solo hay 1 cliente esperamos
                    if (clients.size < 2) {
                        runOnUiThread {
                            turnText.text = "Esperando otro jugador..."
                            colButtons.forEach { it.isEnabled = false }
                        }
                        return
                    }
                    
                    // parsear fichas
                    val piecesArray = json.getJSONArray("objectsList")
                    val piecesList = mutableListOf<GameObject>()
                    for (i in 0 until piecesArray.length()) {
                        val piece = GameObject.fromJSON(piecesArray.getJSONObject(i))
                        piecesList.add(piece)
                    }
                    pieces = piecesList
                    
                    // obtener mi rol
                    if (myRole == null) {
                        myRole = clients.find { it.name == clientName }?.role
                    }
                    
                    val currentTurn = json.optString("currentTurn", "")
                    isMyTurn = myRole == currentTurn
                    
                    // actualizar tablero
                    runOnUiThread {
                        updateBoard()
                        updateTurnText(currentTurn)
                    }
                    
                    // verificar ganador
                    val winner = json.optString("roundWinner", "")
                    if (winner.isNotEmpty()) {
                        runOnUiThread {
                            val winnerName = clients.find { it.role == winner }?.name ?: winner
                            showVictoryScreen(winnerName, winner == myRole)
                        }
                    }
                }
                
                "countdown" -> {
                    val value = json.getInt("value")
                    runOnUiThread {
                        if (value == 0) {
                            Toast.makeText(this, "¡Empieza el juego!", Toast.LENGTH_SHORT).show()
                            turnText.text = "¡EMPIEZA!"
                        } else {
                            turnText.text = "Empieza en: $value"
                        }
                    }
                }
                
                "broadcast" -> {
                    // Recibir mensaje broadcast de otros jugadores
                    val broadcastMessage = json.getString("message")
                    val senderName = json.optString("senderName", "Jugador")
                    runOnUiThread {
                        Toast.makeText(this, "$senderName dice: $broadcastMessage", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Error de comunicación con el servidor", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateBoard() {
        // limpiar tablero
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                board[r][c] = 0
                cells[r][c]?.setImageResource(R.drawable.circle_empty)
            }
        }
        
        var rendered = 0
        
        // dibujar todas las fichas
        for (piece in pieces) {
            if (piece.id.isEmpty()) continue
            
            // calcular posicion en el grid
            val (row, col) = GameObject.calculateGridPosition(piece.x, piece.y)
            
            // solo renderizar fichas dentro del tablero
            if (row >= 0 && row < ROWS && col >= 0 && col < COLS) {
                val drawableRes = when {
                    piece.id.startsWith("R_") -> {
                        board[row][col] = 1
                        R.drawable.circle_red
                    }
                    piece.id.startsWith("Y_") -> {
                        board[row][col] = 2
                        R.drawable.circle_yellow
                    }
                    else -> R.drawable.circle_empty
                }
                
                cells[row][col]?.setImageResource(drawableRes)
                rendered++
            }
        }
    }

    // actualizar el texto del trno
    private fun updateTurnText(currentTurn: String) {
        val colorName = if (currentTurn == "R") "ROJO" else "AMARILLO"
        val text = if (isMyTurn) {
            "TU TURNO ($colorName)"
        } else {
            "TURNO: $colorName"
        }
        turnText.text = text
        turnText.setTextColor(if (isMyTurn) Color.GREEN else Color.BLACK)
        
        // habilitar botones si hay 2 jugadores
        if (clients.size >= 2) {
            colButtons.forEach { it.isEnabled = true }
        }
    }

    // enviar la jugada al server
    private fun sendPlay(col: Int) {
        // buscar ficha disponible de mi color q este fuera del tablero
        val availablePiece = pieces.find { piece ->
            if (piece.role != myRole) return@find false
            
            // calcular si esta dentro del tablero
            val (row, calcCol) = GameObject.calculateGridPosition(piece.x, piece.y)
            
            // si esta fuera del tablero esta disponible
            row == -1 || calcCol == -1
        }
        
        if (availablePiece == null) {
            Toast.makeText(this, "No tienes fichas disponibles", Toast.LENGTH_SHORT).show()
            return
        }
        
        // enviar al server
        val msg = JSONObject().apply {
            put("type", "clientPlay")
            put("column", col)
            put("pieceId", availablePiece.id)
        }
        
        wsClient?.sendJSON(msg)
    }

    // enviar mensaje broadcast al server
    private fun sendBroadcastMessage(message: String) {
        val msg = JSONObject().apply {
            put("type", "broadcast")
            put("message", message)
        }
        wsClient?.sendJSON(msg)
    }


    // mostrar el mensaje de victoria
    private fun showVictoryScreen(winnerName: String, didIWin: Boolean) {
        // deshabilitar botones
        disableAllButtons()
        
        // crear dialogo de victoria
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setCancelable(false)
        
        val message = if (didIWin) {
            "🎉 ¡FELICIDADES! 🎉\n\n¡Has ganado la partida!"
        } else {
            "😔 Has perdido\n\n$winnerName ha ganado la partida"
        }
        
        builder.setTitle(if (didIWin) "¡VICTORIA!" else "Derrota")
        builder.setMessage(message)
        builder.setPositiveButton("Volver al Menú") { _, _ ->
            // cerrar conexion websocket
            wsClient?.disconnect()
            
            // volver a ConfigActivity
            finish()
        }
        
        val dialog = builder.create()
        dialog.show()
    }

    // deshabilitar los botones
    private fun disableAllButtons() {
        colButtons.forEach { it.isEnabled = false }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        wsClient?.disconnect()
    }
}
