package ru.ulstu.soapmessenger.client

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MessengerUser(
    val userId: String,
    val username: String,
)

data class DialogSummary(
    val dialogId: String,
    val interlocutor: MessengerUser,
    val lastMessageContent: String? = null,
    val lastMessageCreatedAt: String? = null,
)

private val FieldShape = RoundedCornerShape(12.dp)
private val SearchResultShape = RoundedCornerShape(8.dp)
private val ButtonHeight = 52.dp
private val SearchResultHeight = 44.dp
private val ListItemHeight = 64.dp
private const val DialogOpenedMessage = "Диалог открыт"

@Composable
fun DialogsScreen(
    dialogs: List<DialogSummary>,
    foundUser: MessengerUser?,
    isLoading: Boolean,
    statusMessage: String?,
    statusIsSuccess: Boolean,
    onRefresh: () -> Unit,
    onFindUser: (username: String) -> Unit,
    onOpenDialog: (otherUserId: String) -> Unit,
    onSearchQueryChanged: () -> Unit = {},
    initialSearchQuery: String = "",
) {
    var searchUsername by remember { mutableStateOf(initialSearchQuery) }
    val controlsEnabled = !isLoading

    LaunchedEffect(statusMessage, statusIsSuccess) {
        if (statusIsSuccess && statusMessage == DialogOpenedMessage) {
            searchUsername = ""
        }
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppPalette.screenBackground)
                .safeContentPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "SOAP Messenger",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp,
                ),
                color = AppPalette.accent,
            )
            Text(
                text = "Диалоги",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = AppPalette.textPrimary,
            )
            Text(
                text = "Найдите собеседника или выберите существующий диалог",
                style = MaterialTheme.typography.bodyMedium,
                color = AppPalette.textSecondary,
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                SearchField(
                    value = searchUsername,
                    onValueChange = { newValue ->
                        if (newValue != searchUsername) {
                            onSearchQueryChanged()
                        }
                        searchUsername = newValue
                    },
                    enabled = controlsEnabled,
                    isLoading = isLoading,
                    onSearch = { onFindUser(searchUsername) },
                )

                foundUser?.let { user ->
                    SearchResultRow(
                        username = user.username,
                        enabled = controlsEnabled,
                        onClick = { onOpenDialog(user.userId) },
                    )
                }
            }

            statusMessage?.let { message ->
                StatusMessageBlock(
                    message = message,
                    isSuccess = statusIsSuccess,
                )
            }

            HorizontalDivider(color = AppPalette.border)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Ваши диалоги",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = AppPalette.textPrimary,
                )
                TextButton(
                    onClick = onRefresh,
                    enabled = controlsEnabled,
                ) {
                    Text(
                        text = "Обновить",
                        color = AppPalette.accent,
                    )
                }
            }

            if (dialogs.isEmpty()) {
                EmptyDialogsState()
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppPalette.surface, FieldShape)
                        .padding(vertical = 4.dp),
                ) {
                    dialogs.forEachIndexed { index, dialog ->
                        DialogListItem(dialog = dialog)
                        if (index < dialogs.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = AppPalette.border,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    isLoading: Boolean,
    onSearch: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .height(ButtonHeight),
        shape = FieldShape,
        placeholder = {
            Text(
                text = "Введите username",
                color = AppPalette.textSecondary,
            )
        },
        trailingIcon = {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = AppPalette.accent,
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(onClick = onSearch, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Найти",
                        tint = AppPalette.accent,
                    )
                }
            }
        },
        colors = fieldColors(),
    )
}

@Composable
private fun SearchResultRow(
    username: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .height(SearchResultHeight)
            .background(AppPalette.surface, SearchResultShape)
            .border(1.dp, AppPalette.border, SearchResultShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = username,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = AppPalette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyDialogsState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppPalette.surface, FieldShape)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "У вас пока нет диалогов",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = AppPalette.textPrimary,
        )
        Text(
            text = "Найдите пользователя выше, чтобы начать переписку",
            style = MaterialTheme.typography.bodyMedium,
            color = AppPalette.textSecondary,
        )
    }
}

@Composable
private fun DialogListItem(dialog: DialogSummary) {
    val previewText = dialog.lastMessageContent ?: "Нет сообщений"
    val timeText = dialog.lastMessageCreatedAt?.let(::formatLastMessageTime)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ListItemHeight)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = dialog.interlocutor.username,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = AppPalette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = previewText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (dialog.lastMessageContent != null) AppPalette.textSecondary else AppPalette.textSecondary.copy(
                    alpha = 0.8f,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (timeText != null) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.bodySmall,
                color = AppPalette.textSecondary,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun StatusMessageBlock(message: String, isSuccess: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isSuccess) AppPalette.statusSuccessBackground else AppPalette.statusErrorBackground,
                shape = FieldShape,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSuccess) AppPalette.statusSuccessText else AppPalette.statusErrorText,
        )
    }
}

private fun formatLastMessageTime(raw: String): String {
    val timeIndex = raw.indexOf('T')
    if (timeIndex < 0 || raw.length < timeIndex + 6) {
        return raw
    }
    val day = raw.substring(8, 10)
    val month = raw.substring(5, 7)
    val time = raw.substring(timeIndex + 1, timeIndex + 6)
    return "$day.$month $time"
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
    name = "Список диалогов",
    showSystemUi = true,
    device = Devices.PIXEL_4,
)
@Composable
private fun DialogsScreenWithDialogsPreview() {
    DialogsScreen(
        dialogs = listOf(
            DialogSummary(
                dialogId = "11111111-1111-1111-1111-111111111111",
                interlocutor = MessengerUser(
                    userId = "22222222-2222-2222-2222-222222222222",
                    username = "alice",
                ),
                lastMessageContent = "Привет! Как дела?",
                lastMessageCreatedAt = "2026-03-12T18:45:00",
            ),
            DialogSummary(
                dialogId = "33333333-3333-3333-3333-333333333333",
                interlocutor = MessengerUser(
                    userId = "44444444-4444-4444-4444-444444444444",
                    username = "bob",
                ),
            ),
        ),
        foundUser = null,
        isLoading = false,
        statusMessage = null,
        statusIsSuccess = false,
        onRefresh = {},
        onFindUser = {},
        onOpenDialog = {},
    )
}

@Preview(
    name = "Пустое состояние",
    showSystemUi = true,
    device = Devices.PIXEL_4,
)
@Composable
private fun DialogsScreenEmptyStatePreview() {
    DialogsScreen(
        dialogs = emptyList(),
        foundUser = null,
        isLoading = false,
        statusMessage = null,
        statusIsSuccess = false,
        onRefresh = {},
        onFindUser = {},
        onOpenDialog = {},
    )
}

@Preview(
    name = "Результат поиска",
    showSystemUi = true,
    device = Devices.PIXEL_4,
)
@Composable
private fun DialogsScreenSearchResultPreview() {
    DialogsScreen(
        dialogs = emptyList(),
        foundUser = MessengerUser(
            userId = "22222222-2222-2222-2222-222222222222",
            username = "alice",
        ),
        isLoading = false,
        statusMessage = null,
        statusIsSuccess = false,
        initialSearchQuery = "alice",
        onRefresh = {},
        onFindUser = {},
        onOpenDialog = {},
    )
}
