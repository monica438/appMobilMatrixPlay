package com.example.appMobilMatrixPlay

import org.json.JSONObject

// datos del cliente q vienen del server
data class ClientData(
    val name: String,
    val color: String,
    val mouseX: Int = 0,
    val mouseY: Int = 0,
    val gridRow: Int = -1,
    val gridCol: Int = -1,
    val role: String? = null // "R" o "Y"
) {
    fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("color", color)
            put("mouseX", mouseX)
            put("mouseY", mouseY)
            put("gridRow", gridRow)
            put("gridCol", gridCol)
            role?.let { put("role", it) }
        }
    }

    companion object {
        fun fromJSON(json: JSONObject): ClientData {
            return ClientData(
                name = json.getString("name"),
                color = json.getString("color"),
                mouseX = json.optInt("mouseX", 0),
                mouseY = json.optInt("mouseY", 0),
                gridRow = json.optInt("gridRow", -1),
                gridCol = json.optInt("gridCol", -1),
                role = if (json.has("role") && !json.isNull("role")) json.getString("role") else null
            )
        }
    }
}
