package com.example.appMobilMatrixPlay

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Actividad de cuenta atrás antes de iniciar el juego
 * Muestra un conteo regresivo de 3 a 1 antes de comenzar la partida
 */
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
