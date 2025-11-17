package com.example.appMobilMatrixPlay

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketClient(private val url: String) {
    
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    
    private val handler = Handler(Looper.getMainLooper())
    
    private var onMessageCallback: ((String) -> Unit)? = null
    private var onOpenCallback: (() -> Unit)? = null
    private var onCloseCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    
    private var shouldReconnect = true
    private var conectado = false
    private var heartbeatRunnable: Runnable? = null
    
    fun connect() {
        val request = Request.Builder()
            .url(url)
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                conectado = true
                Log.d(TAG, "✅ WebSocket CONNECTED: $url")
                handler.post {
                    startHeartbeat()
                    onOpenCallback?.invoke()
                }
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "📩 Received raw: $text")

                try {
                    val json = JSONObject(text)
                    val keys = json.keys()
                    val details = StringBuilder()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        details.append("$k=${json.opt(k)}; ")
                    }
                    Log.d(TAG, "📊 Received parsed: ${details}")
                } catch (e: Exception) {
                    // Not JSON
                }

                handler.post {
                    onMessageCallback?.invoke(text)
                }
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                conectado = false
                Log.d(TAG, "WebSocket closing: code=$code reason=$reason")
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                conectado = false
                Log.d(TAG, "WebSocket closed: code=$code reason=$reason")
                stopHeartbeat()
                handler.post {
                    onCloseCallback?.invoke()
                }
                
                if (shouldReconnect) {
                    scheduleReconnect()
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                conectado = false
                Log.d(TAG, "❌ WebSocket failure: ${t.message}")
                stopHeartbeat()
                handler.post {
                    onErrorCallback?.invoke(t.message ?: "Unknown error")
                }
                
                if (shouldReconnect) {
                    scheduleReconnect()
                }
            }
        })
    }
    
    private fun startHeartbeat() {
        heartbeatRunnable = object : Runnable {
            override fun run() {
                if (conectado) {
                    val ping = JSONObject().apply {
                        put("type", "ping")
                        put("timestamp", System.currentTimeMillis())
                    }
                    sendJSON(ping)
                    Log.d(TAG, "💓 Heartbeat sent")
                }
                handler.postDelayed(this, HEARTBEAT_INTERVAL)
            }
        }
        handler.post(heartbeatRunnable!!)
    }
    
    private fun stopHeartbeat() {
        heartbeatRunnable?.let {
            handler.removeCallbacks(it)
        }
        heartbeatRunnable = null
    }
    
    private fun scheduleReconnect() {
        handler.postDelayed({
            if (shouldReconnect && !conectado) {
                Log.d(TAG, "🔄 Attempting reconnect...")
                connect()
            }
        }, RECONNECT_DELAY)
    }
    
    fun send(message: String) {
        if (conectado) {
            Log.d(TAG, "📤 Sending: $message")
            webSocket?.send(message)
        } else {
            Log.d(TAG, "🚫 Send skipped, not connected: $message")
        }
    }
    
    fun sendJSON(json: JSONObject) {
        send(json.toString())
    }
    
    fun disconnect() {
        shouldReconnect = false
        conectado = false
        stopHeartbeat()
        webSocket?.close(1000, "Client closing")
        webSocket = null
        Log.d(TAG, "🔴 WebSocket disconnected")
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
    
    fun isConnected(): Boolean = conectado
    
    companion object {
        private const val TAG = "WebSocketClient"
        private const val RECONNECT_DELAY = 3000L
        private const val HEARTBEAT_INTERVAL = 15000L
    }
}