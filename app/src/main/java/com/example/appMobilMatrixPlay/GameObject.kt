package com.example.appMobilMatrixPlay

import org.json.JSONObject

/**
 * Representa un objeto del juego (ficha, pelota, etc.)
 */
data class GameObject(
    val id: String,
    val x: Float,
    val y: Float,
    val role: String? = null
) {
    companion object {
        fun fromJSON(json: JSONObject): GameObject {
            return GameObject(
                id = json.optString("id", ""),
                x = json.optDouble("x", 0.0).toFloat(),
                y = json.optDouble("y", 0.0).toFloat(),
                role = json.optString("role", null)
            )
        }
        
        fun calculateGridPosition(x: Float, y: Float): Pair<Int, Int> {
            // Convertir coordenadas del servidor a posición en el grid
            // Esto depende de cómo el servidor envíe las coordenadas
            // Aquí asumimos que las coordenadas ya son del grid (0-5, 0-6)
            val row = y.toInt()
            val col = x.toInt()
            return Pair(row, col)
        }
    }
}
