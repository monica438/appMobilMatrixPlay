package com.example.appMobilMatrixPlay

import org.json.JSONObject

// Ficha del juego
data class GameObject(
    val id: String,
    var x: Int,
    var y: Int,
    var col: Int = -1,
    var row: Int = -1,
    val role: String? = null
) {
    fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("x", x)
            put("y", y)
            put("col", col)
            put("row", row)
            role?.let { put("role", it) }
        }
    }

    companion object {
        fun fromJSON(json: JSONObject): GameObject {
            return GameObject(
                id = json.getString("id"),
                x = json.optInt("x", 0),
                y = json.optInt("y", 0),
                col = json.optInt("col", -1),
                row = json.optInt("row", -1),
                role = if (json.has("role") && !json.isNull("role")) json.getString("role") else null
            )
        }
        
        // calcular row/col desde las coordenads x,y del server
        // el grid empieza en (25, 25) con tamaño de celda 50
        fun calculateGridPosition(x: Int, y: Int): Pair<Int, Int> {
            val startX = 25
            val startY = 25
            val size = 50
            val cols = 7
            val rows = 6
            
            val col = (x - startX) / size
            val row = (y - startY) / size
            
            // verificar si esta dentro del tablero
            if (col < 0 || col >= cols || row < 0 || row >= rows) {
                return Pair(-1, -1)
            }
            
            return Pair(row, col)
        }
    }
}
