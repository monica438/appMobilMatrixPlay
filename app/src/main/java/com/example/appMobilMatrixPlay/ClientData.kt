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
