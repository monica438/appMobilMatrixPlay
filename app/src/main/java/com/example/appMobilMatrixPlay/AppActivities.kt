package com.example.appMobilMatrixPlay

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

// ============================================================================
// SplashActivity
// ============================================================================

/**
 * Pantalla de loading inicial con logo del juego Ping Pong
 */
class SplashActivity : AppCompatActivity() {
    
    private val SPLASH_DELAY = 2500L // 2.5 segundos
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        // Ocultar action bar
        supportActionBar?.hide()
        
        // Después del delay, navegar a ConfigActivity
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, ConfigActivity::class.java))
            finish() // Cerrar splash para que no vuelva con back button
        }, SPLASH_DELAY)
    }
}

// ============================================================================
// ConfigActivity
// ============================================================================

// Pantalla de configuración del servidor
class ConfigActivity : AppCompatActivity() {
    
    private lateinit var inputPlayerName: EditText
    private lateinit var inputHost: EditText
    private lateinit var messageText: TextView
    private lateinit var btnConnect: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)
        
        inputPlayerName = findViewById(R.id.et_protocol)
        inputHost = findViewById(R.id.et_host)
        messageText = findViewById(R.id.txt_message)
        btnConnect = findViewById(R.id.btn_connect)
        
        // Configuración por defecto: Proxmox
        setupDefaultConfig()
        
        btnConnect.setOnClickListener { connectServer() }
    }
    
    private fun setupDefaultConfig() {
        inputHost.setText("matrixplay4.ieti.site")
        messageText.text = ""
    }
    
    private fun connectServer() {
        val playerName = inputPlayerName.text.toString()
        val host = inputHost.text.toString()
        val protocol = "wss" // Protocolo WebSocket Secure (SSL)
        val port = "443" // Puerto 443 con SSL
        
        if (playerName.isEmpty() || host.isEmpty()) {
            messageText.text = "Si us plau, completa tots els camps"
            return
        }
        
        messageText.text = "Connectant..."
        
        // Pasar configuración a WaitingRoomActivity
        val intent = Intent(this, WaitingRoomActivity::class.java).apply {
            putExtra("protocol", protocol)
            putExtra("host", host)
            putExtra("port", port)
            putExtra("playerName", playerName)
        }
        startActivity(intent)
    }
}

// ============================================================================
// WaitingRoomActivity
// ============================================================================

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
    private var myColor: String = ""
    private var player2Name: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private var currentDialog: AlertDialog? = null
    private var colorAssigned = false
    private var isTransferringConnection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_waiting_room)

        getIntentData()
        initViews()
        connectToServer()
    }
    
    private fun getIntentData() {
        protocol = intent.getStringExtra("protocol") ?: "wss"
        host = intent.getStringExtra("host") ?: "matrixplay4.ieti.site"
        port = intent.getStringExtra("port") ?: "443"
        playerName = intent.getStringExtra("playerName") ?: "Jugador"
    }
    
    private fun initViews() {
        txtPlayer1Name = findViewById(R.id.txt_player1_name)
        txtPlayer2Name = findViewById(R.id.txt_player2_name)
        txtPlayer2Label = findViewById(R.id.txt_player2_label)
        txtStatus = findViewById(R.id.txt_status)
        loadingSpinner = findViewById(R.id.loading_spinner)
        player1Icon = findViewById(R.id.player1_icon)
        player2Icon = findViewById(R.id.player2_icon)

        txtPlayer1Name.text = playerName
        player1Connected = true
        player1Icon.setImageResource(R.drawable.rojo)
        player2Icon.setImageResource(R.drawable.gris)
    }

    private fun connectToServer() {
        val url = "$protocol://$host:$port"
        
        txtStatus.text = "Connectant a $url..."
        Log.d(TAG, "🔗 Conectando a: $url")

        webSocketClient = WebSocketClient(url)
        
        webSocketClient?.onOpen {
            runOnUiThread {
                isConnectedToServer = true
                txtStatus.text = "Connectat - Esperant jugador..."
                Toast.makeText(this, "✅ Connectat al servidor", Toast.LENGTH_SHORT).show()
                
                createPlayer()
                
                handler.postDelayed({
                    sendBroadcastMessage("hola")
                }, 500)
                
                // Timeout de seguridad
                handler.postDelayed({
                    if (!player2Connected) {
                        Log.d(TAG, "⏱️ Timeout: Aún esperando jugador 2...")
                        txtStatus.text = "Esperant segon jugador..."
                    }
                }, 30000)
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
        webSocketClient?.let { ws ->
            MessageHandler.crearJugador(playerName, ws)
        }
    }
    
    private fun sendBroadcastMessage(message: String) {
        webSocketClient?.let { ws ->
            MessageHandler.enviarBroadcast(message, playerName, ws)
        }
    }
    
    private fun showPopupMessage(title: String, message: String) {
        runOnUiThread {
            currentDialog?.dismiss()
            
            currentDialog = AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    private fun handleServerMessage(message: String) {
        try {
            Log.d(TAG, "📩 Mensaje recibido: $message")
            
            MessageHandler.procesarMensaje(
                mensaje = message,
                
                onJocData = { jocData ->
                    MessageHandler.procesarJocData(jocData, playerName) { result ->
                        runOnUiThread {
                            handleJocData(result)
                        }
                    }
                },
                
                onBroadcastHola = { value ->
                    runOnUiThread {
                        showPopupMessage("INFORMACIÓ", value)
                    }
                },
                
                onPlayerJoined = { joinedPlayerName ->
                    if (joinedPlayerName != playerName) {
                        runOnUiThread {
                            if (!isConnectedToServer) {
                                Toast.makeText(this, "Error: No connectat al servidor", Toast.LENGTH_SHORT).show()
                                finish()
                                return@runOnUiThread
                            }

                            if (!player2Connected) {
                                player2Connected = true
                                player2Name = joinedPlayerName
                                txtPlayer2Name.text = joinedPlayerName
                                txtPlayer2Name.visibility = View.VISIBLE
                                txtPlayer2Label.text = "NOM\nJUGADOR"
                                txtStatus.text = "Tots dos jugadors connectats!"
                                loadingSpinner.visibility = View.GONE

                                // Solo actualizar icono si ya sabemos nuestro color
                                if (colorAssigned) {
                                    if (myColor == "rojo") {
                                        player2Icon.setImageResource(R.drawable.negro)
                                    } else {
                                        player2Icon.setImageResource(R.drawable.rojo)
                                    }
                                }

                                Log.d(TAG, "✅ Jugador 2 detectado: $joinedPlayerName, Mi color: $myColor")
                                Toast.makeText(this, "Jugador 2 connectat: $joinedPlayerName", Toast.LENGTH_SHORT).show()
                                // Esperar countdown del servidor para iniciar
                            } else {
                                if (player2Name != joinedPlayerName) {
                                    player2Name = joinedPlayerName
                                    txtPlayer2Name.text = joinedPlayerName
                                }
                            }
                        }
                    }
                },
                
                onError = { errorMsg ->
                    runOnUiThread {
                        Toast.makeText(this, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                },
                
                onCountdown = { count ->
                    runOnUiThread {
                        if (count > 0) {
                            // Mostrar countdown del servidor
                            txtStatus.text = "Iniciant en $count..."
                            Log.d(TAG, "⏱️ Countdown del servidor: $count")
                        } else {
                            txtStatus.text = "GO!"
                            Log.d(TAG, "🚀 Countdown completado - Iniciando juego")
                            handler.postDelayed({
                                startMainActivity()
                            }, 500) // Pequeño delay para mostrar "GO!"
                        }
                    }
                }
            )
            
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "❌ Error procesando mensaje: ${e.message}")
            runOnUiThread {
                Toast.makeText(this, "Error processant missatge", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun handleJocData(result: MessageHandler.JocDataResult) {
        Log.d(TAG, "📊 JocData recibido - J1: ${result.jugador1}, J2: ${result.jugador2}, SoyJ1: ${result.soyJugador1}")
        
        // Determinar mi color basándose en la posición del servidor
        if (!colorAssigned) {
            myColor = if (result.soyJugador1) "rojo" else "negro"
            colorAssigned = true
            
            Log.d(TAG, "🎨 Color asignado: $myColor (${if (result.soyJugador1) "Jugador 1 - IZQUIERDA/RED" else "Jugador 2 - DERECHA/BLACK"})")
            
            // Actualizar iconos según mi color
            if (myColor == "rojo") {
                player1Icon.setImageResource(R.drawable.rojo)
                player2Icon.setImageResource(R.drawable.gris)
            } else {
                player1Icon.setImageResource(R.drawable.negro)
                player2Icon.setImageResource(R.drawable.gris)
            }
        }
        
        txtPlayer1Name.text = playerName
        
        if (result.jugador2.isNotEmpty() && result.jugador2 != playerName) {
            if (!player2Connected) {
                player2Connected = true
                player2Name = result.jugador2
                txtPlayer2Name.text = player2Name
                txtPlayer2Name.visibility = View.VISIBLE
                txtStatus.text = "¡Ambos jugadores conectados!"
                loadingSpinner.visibility = View.GONE
                
                // Actualizar icono del jugador 2
                player2Icon.setImageResource(if (myColor == "rojo") R.drawable.negro else R.drawable.rojo)
                
                Log.d(TAG, "✅ Jugador 2 conectado: $player2Name, Mi color: $myColor")
                // El servidor enviará el countdown para iniciar
            }
        } else if (result.jugador1.isNotEmpty() && result.jugador1 != playerName) {
            // El otro jugador es jugador 1 y yo soy jugador 2
            if (!player2Connected) {
                player2Connected = true
                player2Name = result.jugador1
                txtPlayer2Name.text = player2Name
                txtPlayer2Name.visibility = View.VISIBLE
                txtStatus.text = "¡Ambos jugadores conectados!"
                loadingSpinner.visibility = View.GONE
                
                // Actualizar icono del jugador 2
                player2Icon.setImageResource(if (myColor == "rojo") R.drawable.negro else R.drawable.rojo)
                
                Log.d(TAG, "✅ Jugador 1 detectado: $player2Name, Mi color: $myColor")
                // El servidor enviará el countdown para iniciar
            }
        }
    }

    private fun startMainActivity() {
        if (!isConnectedToServer) {
            runOnUiThread {
                Toast.makeText(this, "No es pot iniciar: sense connexió", Toast.LENGTH_SHORT).show()
                finish()
            }
            return
        }
        
        // Si no se ha asignado color, usar rojo por defecto (primer jugador)
        if (!colorAssigned) {
            myColor = "rojo"
            colorAssigned = true
            Log.d(TAG, "⚠️ Color no asignado, usando ROJO por defecto")
        }
        
        // Si no hay jugador 2, usar nombre por defecto
        if (!player2Connected || player2Name.isEmpty()) {
            player2Name = "Esperant..."
        }
        
        Log.d(TAG, "🚀 Iniciando juego - Mi color: $myColor, Jugador 2: $player2Name")
        
        // Marcar que estamos transfiriendo la conexión
        isTransferringConnection = true
        
        // Transferir la conexión WebSocket existente a MainActivity
        Log.d(TAG, "🔄 Transfiriendo conexión WebSocket a MainActivity")
        MainActivity.setWebSocketClient(webSocketClient)
        
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("protocol", protocol)
            putExtra("host", host)
            putExtra("port", port)
            putExtra("playerName", playerName)
            putExtra("player2Name", player2Name)
            putExtra("myColor", myColor)
            putExtra("reuseConnection", true)
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        currentDialog?.dismiss()
        currentDialog = null
        
        // Solo desconectar si NO estamos transfiriendo la conexión
        if (!isTransferringConnection) {
            Log.d(TAG, "❌ Desconectando WebSocket (no hay transferencia)")
            webSocketClient?.disconnect()
        } else {
            Log.d(TAG, "✅ Manteniendo WebSocket activo (transferencia en progreso)")
        }
        
        handler.removeCallbacksAndMessages(null)
    }
}

// ============================================================================
// CountdownActivity
// ============================================================================

class CountdownActivity : AppCompatActivity() {

    private lateinit var txtCountdown: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var currentCount = 3
    
    // Variables para pasar a MainActivity
    private var protocol: String = ""
    private var host: String = ""
    private var port: String = ""
    private var playerName: String = ""
    private var player2Name: String = ""
    private var myColor: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_countdown)
        
        // Ocultar action bar
        supportActionBar?.hide()
        
        // Obtener datos del Intent
        protocol = intent.getStringExtra("protocol") ?: "wss"
        host = intent.getStringExtra("host") ?: "matrixplay4.ieti.site"
        port = intent.getStringExtra("port") ?: "443"
        playerName = intent.getStringExtra("playerName") ?: "Jugador"
        player2Name = intent.getStringExtra("player2Name") ?: "Jugador 2"
        myColor = intent.getStringExtra("myColor") ?: "rojo"
        
        // Inicializar vista
        txtCountdown = findViewById(R.id.txt_countdown)
        
        // Iniciar cuenta atrás
        startCountdown()
    }
    
    private fun startCountdown() {
        txtCountdown.text = currentCount.toString()
        
        handler.postDelayed({
            currentCount--
            
            if (currentCount > 0) {
                // Continuar cuenta atrás
                txtCountdown.text = currentCount.toString()
                startCountdown()
            } else {
                // Cuenta atrás terminada, iniciar juego
                startGame()
            }
        }, 1000) // 1 segundo entre cada número
    }
    
    private fun startGame() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("protocol", protocol)
            putExtra("host", host)
            putExtra("port", port)
            putExtra("playerName", playerName)
            putExtra("player2Name", player2Name)
            putExtra("myColor", myColor)
        }
        startActivity(intent)
        finish() // Cerrar CountdownActivity
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}

// ============================================================================
// GameOverActivity
// ============================================================================

class GameOverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ocultar action bar para pantalla completa
        supportActionBar?.hide()
        
        setContentView(R.layout.activity_game_over)

        val txtResult = findViewById<TextView>(R.id.txt_game_result)
        val btnBack = findViewById<Button>(R.id.btn_back)
        val btnExit = findViewById<Button>(R.id.btn_exit)

        // Obtener el resultado del intent
        val didWin = intent.getBooleanExtra("didWin", false)
        
        if (didWin) {
            txtResult.text = "VICTÒRIA"
        } else {
            txtResult.text = "DERROTA"
        }

        btnBack.setOnClickListener {
            // Volver a la pantalla de configuración
            val intent = Intent(this, ConfigActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }

        btnExit.setOnClickListener {
            // Salir de la aplicación
            finishAffinity()
        }
    }
}
