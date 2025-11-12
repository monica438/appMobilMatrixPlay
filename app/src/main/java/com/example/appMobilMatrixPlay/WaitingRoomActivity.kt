package com.example.appMobilMatrixPlay

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import org.json.JSONArray

class WaitingRoomActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WaitingRoomActivity"
    }

    private lateinit var txtPlayer1Name: TextView
    private lateinit var txtPlayer2Name: TextView
    private lateinit var txtPlayer2Label: TextView
    private lateinit var txtStatus: TextView
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var player1Icon: ImageView
    private lateinit var player2Icon: ImageView

    private var webSocketClient: WebSocketClient? = null
    private var protocol: String = ""
    private var host: String = ""
    private var port: String = ""
    private var playerName: String = ""
    
    private var player1Connected = false
    private var player2Connected = false
    private var isConnectedToServer = false
    private var myColor: String = "rojo" 
    private var player2Name: String = "" // Guardar nombre del jugador 2
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_waiting_room)

        // Obtener datos del Intent
        protocol = intent.getStringExtra("protocol") ?: "wss"
        host = intent.getStringExtra("host") ?: "matrixplay4.ieti.site"
        port = intent.getStringExtra("port") ?: "443"
        playerName = intent.getStringExtra("playerName") ?: "Jugador"

        // Inicializar vistas
        txtPlayer1Name = findViewById(R.id.txt_player1_name)
        txtPlayer2Name = findViewById(R.id.txt_player2_name)
        txtPlayer2Label = findViewById(R.id.txt_player2_label)
        txtStatus = findViewById(R.id.txt_status)
        loadingSpinner = findViewById(R.id.loading_spinner)
        player1Icon = findViewById(R.id.player1_icon)
        player2Icon = findViewById(R.id.player2_icon)

        // Mostrar el nombre del jugador actual
        txtPlayer1Name.text = playerName
        player1Connected = true
        
        // El jugador actual es siempre el primer jugador (ROJO) hasta que el servidor diga lo contrario
        // Inicialmente mostramos rojo para el jugador actual
        player1Icon.setImageResource(R.drawable.rojo)
        // El jugador 2 se muestra en gris hasta que se conecte
        player2Icon.setImageResource(R.drawable.gris)

        // Conectar al servidor WebSocket
        connectToServer()
    }

    private fun connectToServer() {
        val url = "$protocol://$host:$port"
        
        txtStatus.text = "Connectant a $url..."
        Toast.makeText(this, "Connectant a $url...", Toast.LENGTH_SHORT).show()

        webSocketClient = WebSocketClient(url)
        
        webSocketClient?.onOpen {
            runOnUiThread {
                isConnectedToServer = true
                txtStatus.text = "Esperant jugador..."
                Toast.makeText(this, "Connectat al servidor", Toast.LENGTH_SHORT).show()
                
                // Crear jugador en el servidor
                createPlayer()
                
                // Enviar mensaje broadcast para anunciar nuestra conexión
                handler.postDelayed({
                    sendBroadcastMessage("hola")
                }, 500)
            }
        }
        
        webSocketClient?.onMessage { text ->
            handleServerMessage(text)
        }
        
        webSocketClient?.onError { error ->
            runOnUiThread {
                isConnectedToServer = false
                txtStatus.text = "Error de connexió: $error"
                loadingSpinner.visibility = View.GONE
                Toast.makeText(this, "Error al connectar: $error\nTornant enrere...", Toast.LENGTH_LONG).show()
                
                // Volver a ConfigActivity después de 2 segundos
                handler.postDelayed({
                    finish()
                }, 2000)
            }
        }
        
        webSocketClient?.onClose {
            runOnUiThread {
                if (isConnectedToServer && !player2Connected) {
                    txtStatus.text = "Connexió tancada"
                    Toast.makeText(this, "Connexió perduda. Tornant enrere...", Toast.LENGTH_SHORT).show()
                    handler.postDelayed({
                        finish()
                    }, 1500)
                }
            }
        }

        webSocketClient?.connect()
    }

    private fun createPlayer() {
        // Enviar mensaje para establecer el nombre del jugador (igual que el cliente Java)
        val json = JSONObject().apply {
            put("type", "setName")
            put("value", playerName)
        }
        webSocketClient?.sendJSON(json)
    }
    
    private fun sendBroadcastMessage(message: String) {
        val json = JSONObject().apply {
            put("type", "broadcast")
            put("message", message)
            put("senderName", playerName)
        }
        webSocketClient?.sendJSON(json)
    }

    private fun showPopupMessage(title: String, message: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    private fun handleServerMessage(message: String) {
        try {
            // Log del mensaje recibido para debug
            Log.d(TAG, "📩 Mensaje recibido: $message")
            
            val json = JSONObject(message)
            val type = json.optString("type", "")

            when (type) {
                "jocData" -> {
                    // Datos del juego - extraer información de jugadores
                    val jugadors = json.optJSONArray("Jugadors")
                    Log.d(TAG, "🎮 jocData recibido - Jugadors: $jugadors")

                    if (jugadors != null && jugadors.length() > 0) {
                        runOnUiThread {
                            // Asegurarse de mostrar nuestro propio nombre en la posición local
                            txtPlayer1Name.text = playerName
                            player1Icon.setImageResource(R.drawable.rojo)

                            // Buscar el otro jugador en la lista y actualizar si es distinto
                            var other: String? = null
                            for (i in 0 until jugadors.length()) {
                                val n = jugadors.optString(i, "")
                                if (n.isNotEmpty() && n != playerName) {
                                    other = n
                                    break
                                }
                            }

                            if (!other.isNullOrEmpty()) {
                                if (!player2Connected) {
                                    player2Connected = true
                                    player2Name = other
                                    txtPlayer2Name.text = other
                                    txtPlayer2Name.visibility = View.VISIBLE
                                    txtPlayer2Label.text = "NOM\nJUGADOR"
                                    txtStatus.text = "Tots dos jugadors connectats!"
                                    loadingSpinner.visibility = View.GONE

                                    // Configurar icono del jugador 2 según nuestro color
                                    if (myColor == "rojo") {
                                        player2Icon.setImageResource(R.drawable.negro)
                                    } else {
                                        player2Icon.setImageResource(R.drawable.rojo)
                                    }

                                    Log.d(TAG, "✅ Jugador 2 detectado: $other")
                                    Toast.makeText(this, "Jugador 2 connectat: $other", Toast.LENGTH_SHORT).show()
                                    startGameWithDelay()
                                } else {
                                    // Si ya estaba marcado como conectado, solo actualizar el nombre si cambia
                                    if (player2Name != other) {
                                        player2Name = other
                                        txtPlayer2Name.text = other
                                    }
                                }
                            }
                        }
                    }
                }
                
                "colorAssignment" -> {
                    // El servidor nos asigna un color
                    myColor = json.optString("color", "rojo")
                    runOnUiThread {
                        // Actualizar el icono del jugador 1 según el color asignado
                        if (myColor == "rojo") {
                            player1Icon.setImageResource(R.drawable.rojo)
                        } else if (myColor == "negro") {
                            player1Icon.setImageResource(R.drawable.negro)
                        }
                        Toast.makeText(this, "Color assignat: $myColor", Toast.LENGTH_SHORT).show()
                    }
                }
                
                "broadcast" -> {
                    val broadcastMessage = json.getString("message")
                    val senderName = json.optString("senderName", "Jugador")
                    
                    // Mostrar mensaje en popup solo si no es el mensaje inicial "hola"
                    if (broadcastMessage != "hola") {
                        showPopupMessage("Missatge de $senderName", broadcastMessage)
                    }
                    
                    // Si recibimos un broadcast de otro jugador (no nosotros mismos)
                    if (senderName != playerName && !player2Connected) {
                        runOnUiThread {
                            // Verificar que estamos conectados antes de proceder
                            if (!isConnectedToServer) {
                                Toast.makeText(this, "Error: No connectat al servidor", Toast.LENGTH_SHORT).show()
                                finish()
                                return@runOnUiThread
                            }

                            // Asegurarse de mostrar nuestro nombre en la posición local
                            txtPlayer1Name.text = playerName

                            // Marcar que el jugador 2 se ha conectado
                            player2Connected = true
                            player2Name = senderName
                            txtPlayer2Name.text = senderName
                            txtPlayer2Name.visibility = View.VISIBLE
                            txtPlayer2Label.text = "NOM\nJUGADOR"
                            txtStatus.text = "Tots dos jugadors connectats!"
                            loadingSpinner.visibility = View.GONE

                            // Configurar icono del jugador 2 según nuestro color
                            if (myColor == "rojo") {
                                player2Icon.setImageResource(R.drawable.negro)
                            } else {
                                player2Icon.setImageResource(R.drawable.rojo)
                            }

                            // Esperar 3 segundos y luego iniciar el juego
                            startGameWithDelay()
                        }
                    }
                }
                
                "playerJoined" -> {
                    // Mensaje alternativo para cuando un jugador se une
                    val joinedPlayerName = json.optString("playerName", "Jugador")
                    
                    if (joinedPlayerName != playerName) {
                        runOnUiThread {
                            if (!isConnectedToServer) {
                                Toast.makeText(this, "Error: No connectat al servidor", Toast.LENGTH_SHORT).show()
                                finish()
                                return@runOnUiThread
                            }

                            // Evitar dobles entradas: si ya hay player2Connected y el nombre es distinto, actualizarlo
                            if (!player2Connected) {
                                player2Connected = true
                                player2Name = joinedPlayerName
                                txtPlayer2Name.text = joinedPlayerName
                                txtPlayer2Name.visibility = View.VISIBLE
                                txtPlayer2Label.text = "NOM\nJUGADOR"
                                txtStatus.text = "Tots dos jugadors connectats!"
                                loadingSpinner.visibility = View.GONE

                                if (myColor == "rojo") {
                                    player2Icon.setImageResource(R.drawable.negro)
                                } else {
                                    player2Icon.setImageResource(R.drawable.rojo)
                                }

                                startGameWithDelay()
                            } else {
                                // Si estaba conectado, si el nombre difiere, actualizar la vista
                                if (player2Name != joinedPlayerName) {
                                    player2Name = joinedPlayerName
                                    txtPlayer2Name.text = joinedPlayerName
                                }
                            }
                        }
                    }
                }
                
                "serverData" -> {
                    val data = json.optString("data", "Sense dades")
                    runOnUiThread {
                        Toast.makeText(this, "Dades: $data", Toast.LENGTH_LONG).show()
                    }
                }
                
                "countdown" -> {
                    val count = json.optInt("count", 0)
                    runOnUiThread {
                        txtStatus.text = "Iniciant en $count..."
                        Toast.makeText(this, "Compte enrere: $count", Toast.LENGTH_SHORT).show()
                    }
                }
                
                else -> {
                    // Log de mensajes desconocidos
                    Log.d(TAG, "⚠️ Tipo desconocido: $type | Mensaje completo: $message")
                    
                    // Intentar extraer nombre de jugador de cualquier mensaje
                    val playerNameFromMsg = json.optString("playerName", "")
                    val senderName = json.optString("senderName", "")
                    val name = json.optString("name", "")
                    
                    // Si encontramos un nombre diferente al nuestro, asumir que es el jugador 2
                    val otherPlayerName = when {
                        playerNameFromMsg.isNotEmpty() && playerNameFromMsg != playerName -> playerNameFromMsg
                        senderName.isNotEmpty() && senderName != playerName -> senderName
                        name.isNotEmpty() && name != playerName -> name
                        else -> ""
                    }
                    
                    Log.d(TAG, "🔍 Buscando jugador 2 - Encontrado: '$otherPlayerName'")
                    
                    if (otherPlayerName.isNotEmpty() && !player2Connected) {
                        runOnUiThread {
                            if (!isConnectedToServer) {
                                Toast.makeText(this, "Error: No connectat al servidor", Toast.LENGTH_SHORT).show()
                                finish()
                                return@runOnUiThread
                            }
                            
                            player2Connected = true
                            txtPlayer2Name.text = otherPlayerName
                            txtPlayer2Name.visibility = View.VISIBLE
                            txtPlayer2Label.text = "NOM\nJUGADOR"
                            txtStatus.text = "Tots dos jugadors connectats!"
                            loadingSpinner.visibility = View.GONE
                            
                            if (myColor == "rojo") {
                                player2Icon.setImageResource(R.drawable.negro)
                            } else {
                                player2Icon.setImageResource(R.drawable.rojo)
                            }
                            
                            Log.d(TAG, "✅ Jugador 2 detectado: $otherPlayerName")
                            Toast.makeText(this, "Jugador 2: $otherPlayerName", Toast.LENGTH_SHORT).show()
                            startGameWithDelay()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "❌ Error procesando mensaje: ${e.message}")
            runOnUiThread {
                Toast.makeText(this, "Error processant missatge", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startGameWithDelay() {
        // Doble verificación: Solo iniciar el juego si estamos conectados al servidor
        if (!isConnectedToServer) {
            runOnUiThread {
                Toast.makeText(this, "No es pot iniciar: sense connexió", Toast.LENGTH_SHORT).show()
                finish()
            }
            return
        }
        
        // Verificar también que el jugador 2 esté conectado
        if (!player2Connected) {
            runOnUiThread {
                Toast.makeText(this, "No es pot iniciar: esperant jugador 2", Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        handler.postDelayed({
            // Triple verificación antes de iniciar
            if (isConnectedToServer && player2Connected) {
                val intent = Intent(this, CountdownActivity::class.java).apply {
                    putExtra("protocol", protocol)
                    putExtra("host", host)
                    putExtra("port", port)
                    putExtra("playerName", playerName)
                    putExtra("player2Name", player2Name)
                    putExtra("myColor", myColor)
                }
                startActivity(intent)
                finish() // Cerrar WaitingRoomActivity
            } else {
                Toast.makeText(this, "Error: Connexió perduda", Toast.LENGTH_SHORT).show()
                finish()
            }
        }, 3000) // 3 segundos de delay
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketClient?.disconnect()
        handler.removeCallbacksAndMessages(null)
    }
}
