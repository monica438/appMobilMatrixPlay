package com.example.appMobilMatrixPlay

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView
    private lateinit var txtPlayerInfo: TextView
    
    // Variables del websocket
    private var wsClient: WebSocketClient? = null
    private var playerName: String = ""
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

        // Inicializar vistas
        txtStatus = findViewById(R.id.txt_status)
        txtPlayerInfo = findViewById(R.id.txt_player_info)

        // Obtener config del servidor
        val protocol = intent.getStringExtra("protocol") ?: "wss"
        val host = intent.getStringExtra("host") ?: "matrixplay4.ieticloudpro.ieti.cat"
        val port = intent.getStringExtra("port") ?: "443"
        playerName = intent.getStringExtra("playerName") ?: "Jugador"
        serverHost = host

        // Mostrar info del jugador
        txtPlayerInfo.text = "Jugador: $playerName"
        
        // Conectar al servidor
        connectToServer("$protocol://$host:$port")
    }

    private fun connectToServer(url: String) {
        txtStatus.text = "Conectando a $url..."
        Toast.makeText(this, "Conectando a $url...", Toast.LENGTH_SHORT).show()
        
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
                Toast.makeText(this, "Error al conectar: $error", Toast.LENGTH_LONG).show()
            }
        }
        
        wsClient?.onClose {
            runOnUiThread {
                txtStatus.text = "Desconectado del servidor"
                Toast.makeText(this, "Conexión cerrada", Toast.LENGTH_SHORT).show()
            }
        }
        
        wsClient?.connect()
    }
    
    private fun handleServerMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.optString("type", "")
            
            when (type) {
                "gameStart" -> {
                    runOnUiThread {
                        txtStatus.text = "¡El juego ha comenzado!"
                        Toast.makeText(this, "¡Empieza el Ping Pong!", Toast.LENGTH_SHORT).show()
                    }
                }
                
                "gameData" -> {
                    // Aquí se procesarán los datos del juego Ping Pong
                    runOnUiThread {
                        txtStatus.text = "Juego en curso..."
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
                    val broadcastMessage = json.getString("message")
                    val senderName = json.optString("senderName", "Jugador")
                    runOnUiThread {
                        Toast.makeText(this, "$senderName: $broadcastMessage", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Error procesando mensaje del servidor", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showGameOver(winner: String) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setCancelable(false)
        
        val didIWin = winner == playerName
        val message = if (didIWin) {
            "🎉 ¡FELICIDADES! 🎉\n\n¡Has ganado la partida!"
        } else {
            "😔 Has perdido\n\n$winner ha ganado la partida"
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
