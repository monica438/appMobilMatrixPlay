package com.example.appMobilMatrixPlay

import android.os.Handler
import android.os.Looper
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// cliente websocket para conectar con el servidor
class WebSocketClient(private val url: String) {
    
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    
    private val handler = Handler(Looper.getMainLooper())
    
    private var onMessageCallback: ((String) -> Unit)? = null
    private var onOpenCallback: (() -> Unit)? = null
    private var onCloseCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    
    private var shouldReconnect = true
    private var conectado = false
    
    fun connect() {
        val request = Request.Builder()
            .url(url)
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                conectado = true
                handler.post {
                    onOpenCallback?.invoke()
                }
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                handler.post {
                    onMessageCallback?.invoke(text)
                }
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                conectado = false
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                conectado = false
                handler.post {
                    onCloseCallback?.invoke()
                }
                
                if (shouldReconnect) {
                    scheduleReconnect()
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                conectado = false
                handler.post {
                    onErrorCallback?.invoke(t.message ?: "Unknown error")
                }
                
                if (shouldReconnect) {
                    scheduleReconnect()
                }
            }
        })
    }
    
    private fun scheduleReconnect() {
        handler.postDelayed({
            if (shouldReconnect && !conectado) {
                connect()
            }
        }, RECONNECT_DELAY)
    }
    
    fun send(message: String) {
        if (conectado) {
            webSocket?.send(message)
        }
    }
    
    fun sendJSON(json: JSONObject) {
        send(json.toString())
    }
    
    fun disconnect() {
        shouldReconnect = false
        conectado = false
        webSocket?.close(1000, "Client closing")
        webSocket = null
    }
    
    fun onMessage(callback: (String) -> Unit) {
        onMessageCallback = callback
    }
    
    fun onOpen(callback: () -> Unit) {
        onOpenCallback = callback
    }
    
    fun onClose(callback: () -> Unit) {
        onCloseCallback = callback
    }
    
    fun onError(callback: (String) -> Unit) {
        onErrorCallback = callback
    }
    
    companion object {
        private const val TAG = "WebSocketClient"
        private const val RECONNECT_DELAY = 5000L // 5 segs
    }
}
