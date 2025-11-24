package com.example.appMobilMatrixPlay

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class GameOverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
