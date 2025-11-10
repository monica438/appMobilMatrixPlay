package com.example.appMobilMatrixPlay

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

// pantalla de configuracion del servidor
class ConfigActivity : AppCompatActivity() {
    
    private lateinit var inputProtocol: EditText
    private lateinit var inputHost: EditText
    private lateinit var inputPort: EditText
    private lateinit var messageText: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnLocal: Button
    private lateinit var btnProxmox: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)
        
        inputProtocol = findViewById(R.id.et_protocol)
        inputHost = findViewById(R.id.et_host)
        inputPort = findViewById(R.id.et_port)
        messageText = findViewById(R.id.txt_message)
        btnConnect = findViewById(R.id.btn_connect)
        btnLocal = findViewById(R.id.btn_local)
        btnProxmox = findViewById(R.id.btn_proxmox)
        
        // config por defecto
        setupLocalConfig()
        
        btnConnect.setOnClickListener { connectServer() }
        btnLocal.setOnClickListener { setupLocalConfig() }
        btnProxmox.setOnClickListener { setupProxmoxConfig() }
    }
    
    private fun setupLocalConfig() {
        inputProtocol.setText("ws")

        // hay que usar la ip 10.0.2.2 para conectar desde el emulador
        inputHost.setText("10.0.2.2")
        inputPort.setText("3000")
        messageText.text = ""
    }
    
    private fun setupProxmoxConfig() {
        inputProtocol.setText("wss")
        inputHost.setText("matrixplay4.ieticloudpro.ieti.cat")
        inputPort.setText("443")
        messageText.text = ""
    }
    
    private fun connectServer() {
        val protocol = inputProtocol.text.toString()
        val host = inputHost.text.toString()
        val port = inputPort.text.toString()
        
        if (protocol.isEmpty() || host.isEmpty() || port.isEmpty()) {
            messageText.text = "Por favor, completa todos los campos"
            return
        }
        
        messageText.text = "Conectando..."
        
        // pasar config a MainActivity
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("protocol", protocol)
            putExtra("host", host)
            putExtra("port", port)
        }
        startActivity(intent)
    }
}
