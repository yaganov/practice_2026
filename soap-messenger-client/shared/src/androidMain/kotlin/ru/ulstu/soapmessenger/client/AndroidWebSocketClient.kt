package ru.ulstu.soapmessenger.client

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class WebSocketMessageEvent(
    val messageId: String,
    val dialogId: String,
    val senderId: String,
    val content: String,
    val createdAt: String,
)

class AndroidWebSocketClient(
    private val callback: Callback,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build(),
) {
    interface Callback {
        fun onConnectionStateChanged(state: WebSocketConnectionState)
        fun onConnectionLost()
        fun onMessage(event: WebSocketMessageEvent)
        fun onError(code: String, message: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val reconnectRunnable = Runnable { startConnectionAttempt() }

    private var connectionGeneration = 0L
    private var activeAttemptGeneration = 0L
    private var activeWebSocket: WebSocket? = null
    private var token: String? = null
    private var closedByUser = false

    fun connect(jwtToken: String) {
        closedByUser = false
        token = jwtToken
        mainHandler.removeCallbacks(reconnectRunnable)
        startConnectionAttempt()
    }

    fun close() {
        closedByUser = true
        connectionGeneration++
        activeAttemptGeneration = connectionGeneration
        token = null
        mainHandler.removeCallbacks(reconnectRunnable)
        activeWebSocket?.close(1000, null)
        activeWebSocket = null
        notifyState(WebSocketConnectionState.Disconnected)
    }

    fun isConnected(): Boolean = activeWebSocket != null

    fun sendMessage(dialogId: String, clientMessageId: String, content: String): Boolean {
        val socket = activeWebSocket ?: return false
        val payload = JSONObject()
            .put("type", "sendMessage")
            .put("dialogId", dialogId)
            .put("clientMessageId", clientMessageId)
            .put("content", content)
            .toString()
        return socket.send(payload)
    }

    private fun startConnectionAttempt() {
        val jwtToken = token ?: return
        val attemptGeneration = ++connectionGeneration
        activeAttemptGeneration = attemptGeneration

        notifyState(WebSocketConnectionState.Connecting)

        val previousSocket = activeWebSocket
        activeWebSocket = null
        previousSocket?.close(1000, null)

        val request = Request.Builder()
            .url(WS_URL)
            .addHeader("Authorization", "Bearer $jwtToken")
            .build()
        httpClient.newWebSocket(request, object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isCurrentAttempt(attemptGeneration)) {
                    return
                }
                mainHandler.removeCallbacks(reconnectRunnable)
                activeWebSocket = webSocket
                notifyState(WebSocketConnectionState.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrentAttempt(attemptGeneration)) {
                    return
                }
                parseIncoming(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrentAttempt(attemptGeneration)) {
                    return
                }
                handleDisconnect(attemptGeneration)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!isCurrentAttempt(attemptGeneration)) {
                    return
                }
                handleDisconnect(attemptGeneration)
            }
        })
    }

    private fun handleDisconnect(attemptGeneration: Long) {
        if (!isCurrentAttempt(attemptGeneration)) {
            return
        }
        activeWebSocket = null
        callback.onConnectionLost()
        if (closedByUser || token == null) {
            notifyState(WebSocketConnectionState.Disconnected)
            return
        }
        notifyState(WebSocketConnectionState.Reconnecting)
        mainHandler.removeCallbacks(reconnectRunnable)
        mainHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS)
    }

    private fun isCurrentAttempt(attemptGeneration: Long): Boolean {
        return attemptGeneration == activeAttemptGeneration && !closedByUser
    }

    private fun parseIncoming(text: String) {
        try {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "message" -> callback.onMessage(
                    WebSocketMessageEvent(
                        messageId = json.optString("messageId"),
                        dialogId = json.optString("dialogId"),
                        senderId = json.optString("senderId"),
                        content = json.optString("content"),
                        createdAt = json.optString("createdAt"),
                    ),
                )
                "error" -> callback.onError(
                    json.optString("code"),
                    json.optString("message"),
                )
            }
        } catch (_: Exception) {
            // ignore malformed payloads
        }
    }

    private fun notifyState(state: WebSocketConnectionState) {
        mainHandler.post { callback.onConnectionStateChanged(state) }
    }

    private companion object {
        const val WS_URL = "ws://10.0.2.2:8080/websocket"
        const val RECONNECT_DELAY_MS = 3_000L
    }
}
