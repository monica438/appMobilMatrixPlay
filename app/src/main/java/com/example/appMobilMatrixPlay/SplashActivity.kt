package com.example.appMobilMatrixPlay

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

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
