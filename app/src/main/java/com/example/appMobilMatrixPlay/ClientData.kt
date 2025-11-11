package com.example.appMobilMatrixPlay

import org.json.JSONObject

/**
 * Representa los datos de un cliente conectado al servidor
 */
data class ClientData(
    val name: String,
    val role: String? = null
) {
    companion object {
        fun fromJSON(json: JSONObject): ClientData {
            return ClientData(
                name = json.optString("name", ""),
                role = json.optString("role", null)
            )
        }
    }
}
