package ru.ulstu.soapmessenger.client

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val jwtToken = mutableStateOf<String?>(null)
    private val currentUserId = mutableStateOf("")
    private val soapClient = AndroidSoapClient()
    private lateinit var webSocketClient: AndroidWebSocketClient
    private val mainHandler = Handler(Looper.getMainLooper())

    private val isLoading = mutableStateOf(false)
    private val statusMessage = mutableStateOf<String?>(null)
    private val statusIsSuccess = mutableStateOf(false)
    private val isLoggedIn = mutableStateOf(false)
    private val registrationSuccessSignal = mutableStateOf(0)

    private val dialogs = mutableStateOf<List<DialogSummary>>(emptyList())
    private val foundUser = mutableStateOf<MessengerUser?>(null)
    private val selectedDialog = mutableStateOf<DialogSummary?>(null)

    private val chatMessages = mutableStateOf<List<ChatMessage>>(emptyList())
    private val chatHistoryLoading = mutableStateOf(false)
    private val chatHistoryError = mutableStateOf<String?>(null)
    private val wsConnectionState = mutableStateOf(WebSocketConnectionState.Disconnected)

    private var dialogsRefreshScheduled = false
    private var chatHistoryRequestId = 0L
    private var dialogsRequestId = 0L
    private val pendingTimeoutRunnable = Runnable { failPendingSendingMessage() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        webSocketClient = AndroidWebSocketClient(
            callback = object : AndroidWebSocketClient.Callback {
                override fun onConnectionStateChanged(state: WebSocketConnectionState) {
                    updateOnMain { handleWebSocketStateChanged(state) }
                }

                override fun onConnectionLost() {
                    updateOnMain { failPendingSendingMessage() }
                }

                override fun onMessage(event: WebSocketMessageEvent) {
                    handleIncomingMessage(event)
                }

                override fun onError(code: String, message: String) {
                    updateOnMain { failPendingSendingMessage() }
                }
            },
        )

        setContent {
            val token by jwtToken

            if (token == null) {
                val loading by isLoading
                val message by statusMessage
                val successStatus by statusIsSuccess
                val loggedIn by isLoggedIn
                val regSuccessSignal by registrationSuccessSignal

                AuthScreen(
                    isLoading = loading,
                    statusMessage = message,
                    statusIsSuccess = successStatus,
                    isLoggedIn = loggedIn,
                    registrationSuccessSignal = regSuccessSignal,
                    onRegister = { username, password ->
                        runSoapCall {
                            when (val result = soapClient.registerUser(username, password)) {
                                is SoapCallResult.Ok -> updateOnMain {
                                    statusIsSuccess.value = true
                                    registrationSuccessSignal.value += 1
                                    statusMessage.value = "Регистрация выполнена. Войдите в аккаунт."
                                }
                                is SoapCallResult.Fault -> updateOnMain {
                                    statusIsSuccess.value = false
                                    statusMessage.value = result.message
                                }
                                SoapCallResult.NetworkError -> updateOnMain {
                                    statusIsSuccess.value = false
                                    statusMessage.value = "Ошибка сети. Проверьте подключение."
                                }
                            }
                        }
                    },
                    onAuthenticate = { username, password ->
                        runSoapCall {
                            when (val result = soapClient.authenticateUser(username, password)) {
                                is SoapCallResult.Ok -> {
                                    val tokenValue = result.value
                                    val userId = extractUserIdFromJwt(tokenValue).orEmpty()
                                    updateOnMain {
                                        jwtToken.value = tokenValue
                                        currentUserId.value = userId
                                        isLoggedIn.value = true
                                        statusMessage.value = null
                                        foundUser.value = null
                                    }
                                    webSocketClient.connect(tokenValue)
                                    val requestId = ++dialogsRequestId
                                    applyGetDialogsResult(
                                        requestId,
                                        tokenValue,
                                        soapClient.getDialogs(tokenValue),
                                    )
                                }
                                is SoapCallResult.Fault -> updateOnMain {
                                    statusIsSuccess.value = false
                                    statusMessage.value = result.message
                                }
                                SoapCallResult.NetworkError -> updateOnMain {
                                    statusIsSuccess.value = false
                                    statusMessage.value = "Ошибка сети. Проверьте подключение."
                                }
                            }
                        }
                    },
                )
            } else {
                val activeDialog by selectedDialog
                val openDialog = activeDialog
                if (openDialog != null) {
                    val messages by chatMessages
                    val historyLoading by chatHistoryLoading
                    val historyError by chatHistoryError
                    val wsState by wsConnectionState
                    val userId by currentUserId

                    ChatScreen(
                        interlocutorUsername = openDialog.interlocutor.username,
                        messages = messages,
                        currentUserId = userId,
                        isLoadingHistory = historyLoading,
                        historyError = historyError,
                        wsState = wsState,
                        onBack = { closeChat() },
                        onSendMessage = { content -> sendChatMessage(content) },
                        onRetryMessage = { clientMessageId, content ->
                            retryChatMessage(clientMessageId, content)
                        },
                    )
                } else {
                    val loading by isLoading
                    val message by statusMessage
                    val successStatus by statusIsSuccess
                    val dialogList by dialogs
                    val user by foundUser

                    DialogsScreen(
                        dialogs = dialogList,
                        foundUser = user,
                        isLoading = loading,
                        statusMessage = message,
                        statusIsSuccess = successStatus,
                        onRefresh = { refreshDialogs() },
                        onFindUser = { username -> findUser(username) },
                        onOpenDialog = { otherUserId -> openDialogFromSearch(otherUserId) },
                        onDialogClick = { dialog -> openChat(dialog) },
                        onSearchQueryChanged = { onSearchQueryChanged() },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        cancelPendingTimeout()
        webSocketClient.close()
        super.onDestroy()
    }

    private fun handleWebSocketStateChanged(state: WebSocketConnectionState) {
        val previousState = wsConnectionState.value
        wsConnectionState.value = state
        if (state == WebSocketConnectionState.Connected &&
            previousState != WebSocketConnectionState.Connected
        ) {
            reloadOpenChatHistoryAfterReconnect()
        }
    }

    private fun reloadOpenChatHistoryAfterReconnect() {
        val dialogId = selectedDialog.value?.dialogId ?: return
        if (jwtToken.value == null) {
            return
        }
        loadChatHistory(dialogId, backgroundRefresh = true)
    }

    private fun closeChat() {
        cancelPendingTimeout()
        selectedDialog.value = null
        chatMessages.value = emptyList()
        chatHistoryError.value = null
        refreshDialogs()
    }

    private fun openChat(dialog: DialogSummary) {
        cancelPendingTimeout()
        selectedDialog.value = dialog
        chatMessages.value = emptyList()
        chatHistoryError.value = null
        loadChatHistory(dialog.dialogId)
    }

    private fun loadChatHistory(dialogId: String, backgroundRefresh: Boolean = false) {
        val token = jwtToken.value ?: return
        val requestId = ++chatHistoryRequestId
        if (!backgroundRefresh) {
            chatHistoryLoading.value = true
            chatHistoryError.value = null
        }
        runInBackground {
            val result = soapClient.getMessageHistory(token, dialogId)
            updateOnMain {
                applyChatHistoryResult(requestId, token, dialogId, backgroundRefresh, result)
            }
        }
    }

    private fun applyChatHistoryResult(
        requestId: Long,
        token: String,
        dialogId: String,
        backgroundRefresh: Boolean,
        result: SoapCallResult<List<ChatMessage>>,
    ) {
        if (requestId != chatHistoryRequestId) {
            return
        }
        if (jwtToken.value != token) {
            return
        }
        if (selectedDialog.value?.dialogId != dialogId) {
            return
        }

        val hasExistingMessages = chatMessages.value.isNotEmpty()
        when (result) {
            is SoapCallResult.Ok -> {
                chatMessages.value = mergeHistoryWithLocal(result.value, chatMessages.value)
                chatHistoryLoading.value = false
                chatHistoryError.value = null
            }
            is SoapCallResult.Fault -> {
                chatHistoryLoading.value = false
                if (!backgroundRefresh || !hasExistingMessages) {
                    chatHistoryError.value = result.message
                }
            }
            SoapCallResult.NetworkError -> {
                chatHistoryLoading.value = false
                if (!backgroundRefresh || !hasExistingMessages) {
                    chatHistoryError.value = NETWORK_ERROR_MESSAGE
                }
            }
        }
    }

    private fun mergeHistoryWithLocal(
        serverMessages: List<ChatMessage>,
        currentMessages: List<ChatMessage>,
    ): List<ChatMessage> {
        val confirmedByMessageId = LinkedHashMap<String, ChatMessage>()
        for (message in serverMessages) {
            val messageId = message.messageId ?: continue
            confirmedByMessageId[messageId] = message.copy(
                deliveryStatus = MessageDeliveryStatus.Confirmed,
            )
        }

        val pendingByClientMessageId = LinkedHashMap<String, ChatMessage>()
        val websocketOnlyConfirmed = LinkedHashMap<String, ChatMessage>()

        for (message in currentMessages) {
            when (message.deliveryStatus) {
                MessageDeliveryStatus.Sending,
                MessageDeliveryStatus.Failed,
                -> {
                    val clientMessageId = message.clientMessageId ?: continue
                    if (clientMessageId !in pendingByClientMessageId) {
                        pendingByClientMessageId[clientMessageId] = message
                    }
                }
                MessageDeliveryStatus.Confirmed -> {
                    val messageId = message.messageId
                    if (messageId != null) {
                        if (messageId !in confirmedByMessageId) {
                            confirmedByMessageId[messageId] = message
                        }
                    } else {
                        val fallbackKey = "${message.createdAt}:${message.content}"
                        if (fallbackKey !in websocketOnlyConfirmed) {
                            websocketOnlyConfirmed[fallbackKey] = message
                        }
                    }
                }
            }
        }

        return sortChatMessages(
            confirmedByMessageId.values +
                websocketOnlyConfirmed.values +
                pendingByClientMessageId.values,
        )
    }

    private fun sortChatMessages(messages: Collection<ChatMessage>): List<ChatMessage> {
        return messages.sortedWith(
            compareBy<ChatMessage> { it.createdAt }
                .thenBy { it.messageId ?: it.clientMessageId.orEmpty() },
        )
    }

    private fun sendChatMessage(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty() || hasPendingSendingMessage()) {
            return
        }
        val dialogId = selectedDialog.value?.dialogId ?: return

        val clientMessageId = UUID.randomUUID().toString()
        val localMessage = ChatMessage(
            messageId = null,
            senderId = currentUserId.value,
            content = trimmed,
            createdAt = currentTimestamp(),
            deliveryStatus = MessageDeliveryStatus.Sending,
            clientMessageId = clientMessageId,
        )
        chatMessages.value = chatMessages.value + localMessage
        dispatchOutgoingMessage(dialogId, clientMessageId, trimmed)
    }

    private fun retryChatMessage(clientMessageId: String, content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty() || hasPendingSendingMessage()) {
            return
        }
        val dialogId = selectedDialog.value?.dialogId ?: return

        cancelPendingTimeout()
        chatMessages.value = chatMessages.value.map { message ->
            if (message.clientMessageId == clientMessageId) {
                message.copy(deliveryStatus = MessageDeliveryStatus.Sending)
            } else {
                message
            }
        }
        dispatchOutgoingMessage(dialogId, clientMessageId, trimmed)
    }

    private fun dispatchOutgoingMessage(dialogId: String, clientMessageId: String, content: String) {
        if (!webSocketClient.isConnected()) {
            markMessageFailed(clientMessageId)
            return
        }
        val sent = webSocketClient.sendMessage(dialogId, clientMessageId, content)
        if (!sent) {
            markMessageFailed(clientMessageId)
            return
        }
        schedulePendingTimeout()
    }

    private fun hasPendingSendingMessage(): Boolean {
        return chatMessages.value.any { it.deliveryStatus == MessageDeliveryStatus.Sending }
    }

    private fun markMessageFailed(clientMessageId: String) {
        cancelPendingTimeout()
        chatMessages.value = chatMessages.value.map { message ->
            if (message.clientMessageId == clientMessageId &&
                message.deliveryStatus == MessageDeliveryStatus.Sending
            ) {
                message.copy(deliveryStatus = MessageDeliveryStatus.Failed)
            } else {
                message
            }
        }
    }

    private fun failPendingSendingMessage() {
        cancelPendingTimeout()
        chatMessages.value = chatMessages.value.map { message ->
            if (message.deliveryStatus == MessageDeliveryStatus.Sending) {
                message.copy(deliveryStatus = MessageDeliveryStatus.Failed)
            } else {
                message
            }
        }
    }

    private fun schedulePendingTimeout() {
        mainHandler.removeCallbacks(pendingTimeoutRunnable)
        mainHandler.postDelayed(pendingTimeoutRunnable, PENDING_MESSAGE_TIMEOUT_MS)
    }

    private fun cancelPendingTimeout() {
        mainHandler.removeCallbacks(pendingTimeoutRunnable)
    }

    private fun handleIncomingMessage(event: WebSocketMessageEvent) {
        updateOnMain {
            updateDialogsOnMessage(event)
            appendChatMessageIfNeeded(event)
        }
    }

    private fun updateDialogsOnMessage(event: WebSocketMessageEvent) {
        val existing = dialogs.value.find { it.dialogId == event.dialogId }
        if (existing != null) {
            dialogs.value = sortDialogs(
                dialogs.value.map { dialog ->
                    if (dialog.dialogId == event.dialogId) {
                        dialog.copy(
                            lastMessageContent = event.content,
                            lastMessageCreatedAt = event.createdAt,
                        )
                    } else {
                        dialog
                    }
                },
            )
        } else {
            scheduleDialogsRefresh()
        }
    }

    private fun appendChatMessageIfNeeded(event: WebSocketMessageEvent) {
        val activeDialog = selectedDialog.value
        if (activeDialog == null || activeDialog.dialogId != event.dialogId) {
            return
        }
        if (chatMessages.value.any { it.messageId == event.messageId }) {
            return
        }

        if (event.senderId == currentUserId.value) {
            cancelPendingTimeout()
            val sendingIndex = chatMessages.value.indexOfFirst {
                it.deliveryStatus == MessageDeliveryStatus.Sending
            }
            if (sendingIndex >= 0) {
                val updated = chatMessages.value.toMutableList()
                updated[sendingIndex] = ChatMessage(
                    messageId = event.messageId,
                    senderId = event.senderId,
                    content = event.content,
                    createdAt = event.createdAt,
                    deliveryStatus = MessageDeliveryStatus.Confirmed,
                )
                chatMessages.value = updated
                return
            }
        }

        chatMessages.value = chatMessages.value + ChatMessage(
            messageId = event.messageId,
            senderId = event.senderId,
            content = event.content,
            createdAt = event.createdAt,
            deliveryStatus = MessageDeliveryStatus.Confirmed,
        )
    }

    private fun scheduleDialogsRefresh() {
        if (dialogsRefreshScheduled) {
            return
        }
        val token = jwtToken.value ?: return
        dialogsRefreshScheduled = true
        val requestId = ++dialogsRequestId
        runInBackground {
            applyGetDialogsResult(requestId, token, soapClient.getDialogs(token))
            updateOnMain { dialogsRefreshScheduled = false }
        }
    }

    private fun onSearchQueryChanged() {
        foundUser.value = null
        statusMessage.value = null
        statusIsSuccess.value = false
    }

    private fun refreshDialogs() {
        val token = jwtToken.value ?: return
        runSoapCall {
            val requestId = ++dialogsRequestId
            applyGetDialogsResult(requestId, token, soapClient.getDialogs(token))
        }
    }

    private fun findUser(username: String) {
        val token = jwtToken.value ?: return
        runSoapCall {
            when (val result = soapClient.findUser(token, username)) {
                is SoapCallResult.Ok -> updateOnMain {
                    foundUser.value = result.value
                    statusIsSuccess.value = false
                    statusMessage.value = null
                }
                is SoapCallResult.Fault -> updateOnMain {
                    foundUser.value = null
                    statusIsSuccess.value = false
                    statusMessage.value = result.message
                }
                SoapCallResult.NetworkError -> updateOnMain {
                    foundUser.value = null
                    statusIsSuccess.value = false
                    statusMessage.value = NETWORK_ERROR_MESSAGE
                }
            }
        }
    }

    private fun openDialogFromSearch(otherUserId: String) {
        val token = jwtToken.value ?: return
        runSoapCall {
            when (val result = soapClient.openOrCreateDialog(token, otherUserId)) {
                is SoapCallResult.Ok -> {
                    val requestId = ++dialogsRequestId
                    applyGetDialogsResult(requestId, token, soapClient.getDialogs(token))
                    updateOnMain {
                        foundUser.value = null
                        openChat(result.value)
                    }
                }
                is SoapCallResult.Fault -> updateOnMain {
                    statusIsSuccess.value = false
                    statusMessage.value = result.message
                }
                SoapCallResult.NetworkError -> updateOnMain {
                    statusIsSuccess.value = false
                    statusMessage.value = NETWORK_ERROR_MESSAGE
                }
            }
        }
    }

    private fun applyGetDialogsResult(
        requestId: Long,
        token: String,
        result: SoapCallResult<List<DialogSummary>>,
    ) {
        updateOnMain {
            if (requestId != dialogsRequestId) {
                return@updateOnMain
            }
            if (jwtToken.value != token) {
                return@updateOnMain
            }

            when (result) {
                is SoapCallResult.Ok -> {
                    dialogs.value = sortDialogs(result.value)
                    statusMessage.value = null
                    statusIsSuccess.value = false
                }
                is SoapCallResult.Fault -> {
                    statusIsSuccess.value = false
                    statusMessage.value = result.message
                }
                SoapCallResult.NetworkError -> {
                    if (dialogs.value.isEmpty()) {
                        statusIsSuccess.value = false
                        statusMessage.value = NETWORK_ERROR_MESSAGE
                    }
                }
            }
        }
    }

    private fun sortDialogs(items: List<DialogSummary>): List<DialogSummary> {
        return items.sortedWith(
            compareByDescending<DialogSummary> { it.lastMessageCreatedAt != null }
                .thenByDescending { it.lastMessageCreatedAt.orEmpty() }
                .thenByDescending { it.dialogId },
        )
    }

    private fun runSoapCall(block: () -> Unit) {
        isLoading.value = true
        runInBackground {
            try {
                block()
            } finally {
                updateOnMain { isLoading.value = false }
            }
        }
    }

    private fun runInBackground(block: () -> Unit) {
        Thread(block).start()
    }

    private fun updateOnMain(block: () -> Unit) {
        runOnUiThread(block)
    }

    private fun currentTimestamp(): String {
        return OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    private fun extractUserIdFromJwt(token: String): String? {
        return try {
            val parts = token.split('.')
            if (parts.size < 2) {
                return null
            }
            val payload = String(
                Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP),
                Charsets.UTF_8,
            )
            JSONObject(payload).getString("sub")
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val NETWORK_ERROR_MESSAGE = "Не удалось выполнить запрос. Проверьте подключение к серверу."
        const val PENDING_MESSAGE_TIMEOUT_MS = 10_000L
    }
}
