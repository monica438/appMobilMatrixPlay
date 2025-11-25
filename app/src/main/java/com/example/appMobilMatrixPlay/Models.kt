package com.example.appMobilMatrixPlay

import org.json.JSONObject

/**
 * Representa los datos de un cliente conectado al servidor
 * Estructura similar al proyecto de escritorio
 */
data class ClientData(
    var name: String,
    var color: String,
    var palaX: Int = 0,
    var palaY: Int = 0,
    var punts: Int = 0
) {
    companion object {
        fun fromJSON(json: JSONObject): ClientData {
            return ClientData(
                name = json.optString("name", "Unknown"),
                color = json.optString("color", "gray"),
                palaX = json.optInt("palaX", 0),
                palaY = json.optInt("palaY", 0),
                punts = json.optInt("punts", 0)
            )
        }
    }
    
    fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("color", color)
            put("palaX", palaX)
            put("palaY", palaY)
            put("punts", punts)
        }
    }
}

/**
 * Representa un objeto del juego (pelota, palas, etc.)
 * Estructura similar al proyecto de escritorio
 */
data class GameObject(
    val id: String,
    var x: Int,
    var y: Int,
    var ancho: Int,
    var alto: Int,
    var color: String
) {
    companion object {
        // Dimensiones virtuales del servidor
        const val VIRTUAL_WIDTH = 600f
        const val VIRTUAL_HEIGHT = 400f
        
        fun fromJSON(json: JSONObject, containerWidth: Int, containerHeight: Int): GameObject {
            val xLog = json.optInt("x", 0)
            val yLog = json.optInt("y", 0)
            val anchoLog = json.optInt("ancho", 1)
            val altoLog = json.optInt("alto", 1)
            
            // Convertir de coordenadas lógicas (600x400) a coordenadas de pantalla
            val xPix = ((xLog / VIRTUAL_WIDTH) * containerWidth).toInt()
            val yPix = ((yLog / VIRTUAL_HEIGHT) * containerHeight).toInt()
            val anchoPix = ((anchoLog / VIRTUAL_WIDTH) * containerWidth).toInt()
            val altoPix = ((altoLog / VIRTUAL_HEIGHT) * containerHeight).toInt()
            
            return GameObject(
                id = json.optString("id", ""),
                x = xPix,
                y = yPix,
                ancho = anchoPix,
                alto = altoPix,
                color = json.optString("color", "gray")
            )
        }
    }
    
    fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("x", x)
            put("y", y)
            put("ancho", ancho)
            put("alto", alto)
            put("color", color)
        }
    }
}
