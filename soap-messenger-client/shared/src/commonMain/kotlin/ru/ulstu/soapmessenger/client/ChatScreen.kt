package ru.ulstu.soapmessenger.client

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

enum class WebSocketConnectionState {
    Connecting,
    Connected,
    Reconnecting,
    Disconnected,
}

enum class MessageDeliveryStatus {
    Confirmed,
    Sending,
    Failed,
}

data class ChatMessage(
    val messageId: String?,
    val senderId: String,
    val content: String,
    val createdAt: String,
    val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.Confirmed,
    val clientMessageId: String? = null,
)

private val FieldShape = RoundedCornerShape(12.dp)
private val BubbleShape = RoundedCornerShape(14.dp)
private val FailedHintSize = 18.dp
private val InputHeight = 52.dp
private val OutgoingBubbleColor = Color(0xFFDCE8F8)

@Composable
fun ChatScreen(
    interlocutorUsername: String,
    messages: List<ChatMessage>,
    currentUserId: String,
    isLoadingHistory: Boolean,
    historyError: String?,
    wsState: WebSocketConnectionState,
    onBack: () -> Unit,
    onSendMessage: (content: String) -> Unit,
    onRetryMessage: (clientMessageId: String, content: String) -> Unit,
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val hasPendingOutgoing = messages.any { it.deliveryStatus == MessageDeliveryStatus.Sending }
    val sendEnabled = messageText.isNotBlank() && !isLoadingHistory && !hasPendingOutgoing

    LaunchedEffect(messages.size, isLoadingHistory) {
        if (!isLoadingHistory && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppPalette.screenBackground)
                .safeContentPadding()
                .imePadding(),
        ) {
            ChatTopBar(
                interlocutorUsername = interlocutorUsername,
                wsState = wsState,
                onBack = onBack,
            )

            HorizontalDivider(color = AppPalette.border)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when {
                    isLoadingHistory -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(28.dp)
                                .align(Alignment.Center),
                            color = AppPalette.accent,
                            strokeWidth = 2.dp,
                        )
                    }
                    historyError != null -> {
                        Text(
                            text = historyError,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppPalette.statusErrorText,
                        )
                    }
                    messages.isEmpty() -> {
                        Text(
                            text = "В этом диалоге пока нет сообщений",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppPalette.textSecondary,
                        )
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 16.dp,
                                vertical = 12.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(messages, key = { itemKey(it) }) { message ->
                                MessageBubble(
                                    message = message,
                                    isOutgoing = isOutgoingMessage(message, currentUserId),
                                    onRetry = onRetryMessage,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = AppPalette.border)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    enabled = !isLoadingHistory,
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .height(InputHeight),
                    shape = FieldShape,
                    placeholder = {
                        Text(
                            text = "Сообщение",
                            color = AppPalette.textSecondary,
                        )
                    },
                    colors = fieldColors(),
                )
                IconButton(
                    onClick = {
                        val content = messageText.trim()
                        if (content.isEmpty() || !sendEnabled) {
                            return@IconButton
                        }
                        onSendMessage(content)
                        messageText = ""
                    },
                    enabled = sendEnabled,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Отправить",
                        tint = if (sendEnabled) AppPalette.accent else AppPalette.textSecondary,
                    )
                }
            }
        }
    }
}

private fun isOutgoingMessage(message: ChatMessage, currentUserId: String): Boolean {
    return message.deliveryStatus != MessageDeliveryStatus.Confirmed ||
        message.senderId == currentUserId
}

@Composable
private fun ChatTopBar(
    interlocutorUsername: String,
    wsState: WebSocketConnectionState,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                tint = AppPalette.textPrimary,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        ) {
            Text(
                text = interlocutorUsername,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = AppPalette.textPrimary,
            )
            Text(
                text = wsState.toStatusText(),
                style = MaterialTheme.typography.bodySmall,
                color = AppPalette.textSecondary,
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isOutgoing: Boolean,
    onRetry: (clientMessageId: String, content: String) -> Unit,
) {
    val bubbleColor = when (message.deliveryStatus) {
        MessageDeliveryStatus.Failed -> AppPalette.statusErrorBackground
        MessageDeliveryStatus.Sending -> OutgoingBubbleColor.copy(alpha = 0.85f)
        MessageDeliveryStatus.Confirmed -> if (isOutgoing) OutgoingBubbleColor else AppPalette.surface
    }
    val alignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        if (isOutgoing && message.deliveryStatus == MessageDeliveryStatus.Failed) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FailedMessageHintIcon()
                MessageBubbleContent(
                    message = message,
                    bubbleColor = bubbleColor,
                    onRetry = onRetry,
                )
            }
        } else {
            MessageBubbleContent(
                message = message,
                bubbleColor = bubbleColor,
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun FailedMessageHintIcon() {
    var hintExpanded by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .size(FailedHintSize)
                .background(AppPalette.statusErrorText, CircleShape)
                .clickable { hintExpanded = !hintExpanded },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "!",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = AppPalette.onAccent,
            )
        }
        DropdownMenu(
            expanded = hintExpanded,
            onDismissRequest = { hintExpanded = false },
        ) {
            Text(
                text = "Сообщение не отправлено.",
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .widthIn(max = 240.dp),
                style = MaterialTheme.typography.bodySmall,
                color = AppPalette.textPrimary,
            )
        }
    }
}

@Composable
private fun MessageBubbleContent(
    message: ChatMessage,
    bubbleColor: Color,
    onRetry: (clientMessageId: String, content: String) -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .background(bubbleColor, BubbleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodyMedium,
            color = AppPalette.textPrimary,
        )
        Text(
            text = formatMessageTime(message.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = AppPalette.textSecondary,
        )
        when (message.deliveryStatus) {
            MessageDeliveryStatus.Sending -> {
                Text(
                    text = "Отправка...",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppPalette.textSecondary,
                )
            }
            MessageDeliveryStatus.Failed -> {
                message.clientMessageId?.let { clientMessageId ->
                    TextButton(
                        onClick = { onRetry(clientMessageId, message.content) },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(
                            text = "Повторить",
                            color = AppPalette.accent,
                        )
                    }
                }
            }
            MessageDeliveryStatus.Confirmed -> Unit
        }
    }
}

private fun itemKey(message: ChatMessage): String {
    return message.messageId
        ?: message.clientMessageId
        ?: "${message.createdAt}:${message.content}"
}

private fun WebSocketConnectionState.toStatusText(): String = when (this) {
    WebSocketConnectionState.Connecting -> "Подключение..."
    WebSocketConnectionState.Connected -> "Подключено"
    WebSocketConnectionState.Reconnecting -> "Переподключение..."
    WebSocketConnectionState.Disconnected -> "Нет подключения"
}

private fun formatMessageTime(raw: String): String {
    val timeIndex = raw.indexOf('T')
    if (timeIndex >= 0 && raw.length >= timeIndex + 6) {
        val day = raw.substring(8, 10)
        val month = raw.substring(5, 7)
        val time = raw.substring(timeIndex + 1, timeIndex + 6)
        return "$day.$month $time"
    }
    if (raw.length >= 16) {
        return raw.substring(11, 16)
    }
    return raw
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppPalette.accent,
    unfocusedBorderColor = AppPalette.border,
    disabledBorderColor = AppPalette.border.copy(alpha = 0.6f),
    cursorColor = AppPalette.accent,
    focusedTextColor = AppPalette.textPrimary,
    unfocusedTextColor = AppPalette.textPrimary,
    focusedContainerColor = AppPalette.surface,
    unfocusedContainerColor = AppPalette.surface,
)

@Preview(
    name = "Все статусы сообщений",
    showSystemUi = true,
    device = Devices.PIXEL_4,
)
@Composable
private fun ChatScreenAllStatusesPreview() {
    ChatScreen(
        interlocutorUsername = "alice",
        messages = listOf(
            ChatMessage(
                messageId = "11111111-1111-1111-1111-111111111111",
                senderId = "22222222-2222-2222-2222-222222222222",
                content = "Привет!",
                createdAt = "2026-03-12T18:40:00+04:00",
            ),
            ChatMessage(
                messageId = "33333333-3333-3333-3333-333333333333",
                senderId = "44444444-4444-4444-4444-444444444444",
                content = "Здравствуйте!",
                createdAt = "2026-03-12T18:45:00+04:00",
            ),
            ChatMessage(
                messageId = null,
                senderId = "44444444-4444-4444-4444-444444444444",
                content = "Сообщение в пути",
                createdAt = "2026-03-12T19:00:00+04:00",
                deliveryStatus = MessageDeliveryStatus.Sending,
                clientMessageId = "55555555-5555-5555-5555-555555555555",
            ),
            ChatMessage(
                messageId = null,
                senderId = "44444444-4444-4444-4444-444444444444",
                content = "Сообщение без сети",
                createdAt = "2026-03-12T19:05:00+04:00",
                deliveryStatus = MessageDeliveryStatus.Failed,
                clientMessageId = "66666666-6666-6666-6666-666666666666",
            ),
        ),
        currentUserId = "44444444-4444-4444-4444-444444444444",
        isLoadingHistory = false,
        historyError = null,
        wsState = WebSocketConnectionState.Reconnecting,
        onBack = {},
        onSendMessage = {},
        onRetryMessage = { _, _ -> },
    )
}

@Preview(
    name = "История сообщений",
    showSystemUi = true,
    device = Devices.PIXEL_4,
)
@Composable
private fun ChatScreenHistoryPreview() {
    ChatScreen(
        interlocutorUsername = "alice",
        messages = listOf(
            ChatMessage(
                messageId = "11111111-1111-1111-1111-111111111111",
                senderId = "22222222-2222-2222-2222-222222222222",
                content = "Привет!",
                createdAt = "2026-03-12T18:40:00+04:00",
            ),
            ChatMessage(
                messageId = "33333333-3333-3333-3333-333333333333",
                senderId = "44444444-4444-4444-4444-444444444444",
                content = "Здравствуйте!",
                createdAt = "2026-03-12T18:45:00+04:00",
            ),
        ),
        currentUserId = "44444444-4444-4444-4444-444444444444",
        isLoadingHistory = false,
        historyError = null,
        wsState = WebSocketConnectionState.Connected,
        onBack = {},
        onSendMessage = {},
        onRetryMessage = { _, _ -> },
    )
}

@Preview(
    name = "Отправка...",
    showSystemUi = true,
    device = Devices.PIXEL_4,
)
@Composable
private fun ChatScreenSendingPreview() {
    ChatScreen(
        interlocutorUsername = "alice",
        messages = listOf(
            ChatMessage(
                messageId = null,
                senderId = "44444444-4444-4444-4444-444444444444",
                content = "Сообщение в пути",
                createdAt = "2026-03-12T19:00:00+04:00",
                deliveryStatus = MessageDeliveryStatus.Sending,
                clientMessageId = "55555555-5555-5555-5555-555555555555",
            ),
        ),
        currentUserId = "44444444-4444-4444-4444-444444444444",
        isLoadingHistory = false,
        historyError = null,
        wsState = WebSocketConnectionState.Connecting,
        onBack = {},
        onSendMessage = {},
        onRetryMessage = { _, _ -> },
    )
}

@Preview(
    name = "Не отправлено",
    showSystemUi = true,
    device = Devices.PIXEL_4,
)
@Composable
private fun ChatScreenFailedPreview() {
    ChatScreen(
        interlocutorUsername = "alice",
        messages = listOf(
            ChatMessage(
                messageId = null,
                senderId = "44444444-4444-4444-4444-444444444444",
                content = "Сообщение без сети",
                createdAt = "2026-03-12T19:00:00+04:00",
                deliveryStatus = MessageDeliveryStatus.Failed,
                clientMessageId = "55555555-5555-5555-5555-555555555555",
            ),
        ),
        currentUserId = "44444444-4444-4444-4444-444444444444",
        isLoadingHistory = false,
        historyError = null,
        wsState = WebSocketConnectionState.Disconnected,
        onBack = {},
        onSendMessage = {},
        onRetryMessage = { _, _ -> },
    )
}
