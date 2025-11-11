package com.example.appMobilMatrixPlay

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

// pantalla de configuracion del servidor
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
        
        // Config por defecto: Proxmox
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
            messageText.text = "Por favor, completa todos los campos"
            return
        }
        
        messageText.text = "Conectando..."
        
        // pasar config a WaitingRoomActivity
        val intent = Intent(this, WaitingRoomActivity::class.java).apply {
            putExtra("protocol", protocol)
            putExtra("host", host)
            putExtra("port", port)
            putExtra("playerName", playerName)
        }
        startActivity(intent)
    }
}
