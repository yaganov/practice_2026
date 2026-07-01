package ru.ulstu.soapmessenger.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val FieldShape = RoundedCornerShape(12.dp)
private val ButtonHeight = 52.dp

private enum class AuthMode {
    Login,
    Register,
}

@Composable
fun AuthScreen(
    isLoading: Boolean,
    statusMessage: String?,
    statusIsSuccess: Boolean,
    isLoggedIn: Boolean,
    registrationSuccessSignal: Int,
    onRegister: (username: String, password: String) -> Unit,
    onAuthenticate: (username: String, password: String) -> Unit,
    startInRegisterMode: Boolean = false,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var mode by remember(startInRegisterMode) {
        mutableStateOf(if (startInRegisterMode) AuthMode.Register else AuthMode.Login)
    }

    LaunchedEffect(registrationSuccessSignal) {
        if (registrationSuccessSignal > 0) {
            mode = AuthMode.Login
        }
    }

    val fieldsEnabled = !isLoading && !isLoggedIn

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppPalette.screenBackground)
                .safeContentPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "SOAP Messenger",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp,
                ),
                color = AppPalette.accent,
            )

            AuthHeader(mode = mode)

            AuthTextField(
                label = "Имя пользователя",
                value = username,
                onValueChange = { username = it },
                enabled = fieldsEnabled,
            )

            AuthTextField(
                label = "Пароль",
                value = password,
                onValueChange = { password = it },
                enabled = fieldsEnabled,
                visualTransformation = PasswordVisualTransformation(),
            )

            Button(
                onClick = {
                    when (mode) {
                        AuthMode.Login -> onAuthenticate(username, password)
                        AuthMode.Register -> onRegister(username, password)
                    }
                },
                enabled = fieldsEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonHeight),
                shape = FieldShape,
                colors = primaryButtonColors(),
            ) {
                Text(
                    text = if (mode == AuthMode.Login) "Войти" else "Зарегистрироваться",
                    fontWeight = FontWeight.Medium,
                )
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(22.dp)
                        .align(Alignment.CenterHorizontally),
                    color = AppPalette.accent,
                    strokeWidth = 2.dp,
                )
            }

            statusMessage?.let { message ->
                StatusMessageBlock(
                    message = message,
                    isSuccess = isLoggedIn || statusIsSuccess,
                )
            }

            TextButton(
                onClick = {
                    if (fieldsEnabled) {
                        mode = if (mode == AuthMode.Login) AuthMode.Register else AuthMode.Login
                    }
                },
                enabled = fieldsEnabled,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text = if (mode == AuthMode.Login) {
                        "Нет аккаунта? Зарегистрироваться"
                    } else {
                        "Уже есть аккаунт? Войти"
                    },
                    color = AppPalette.accent,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AuthTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AppPalette.textSecondary,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            enabled = enabled,
            visualTransformation = visualTransformation,
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonHeight),
            shape = FieldShape,
            colors = fieldColors(),
        )
    }
}

@Composable
private fun AuthHeader(mode: AuthMode) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = if (mode == AuthMode.Login) "Вход" else "Регистрация",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = AppPalette.textPrimary,
        )
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

@Composable
private fun primaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = AppPalette.accent,
    contentColor = AppPalette.onAccent,
    disabledContainerColor = AppPalette.accent.copy(alpha = 0.45f),
    disabledContentColor = AppPalette.onAccent.copy(alpha = 0.8f),
)

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
    name = "Вход",
    showSystemUi = true,
    device = Devices.PIXEL_4,
)
@Composable
private fun AuthScreenLoginPreview() {
    AuthScreen(
        isLoading = false,
        statusMessage = null,
        statusIsSuccess = false,
        isLoggedIn = false,
        registrationSuccessSignal = 0,
        onRegister = { _, _ -> },
        onAuthenticate = { _, _ -> },
    )
}

@Preview(
    name = "Регистрация",
    showSystemUi = true,
    device = Devices.PIXEL_4,
)
@Composable
private fun AuthScreenRegisterPreview() {
    AuthScreen(
        isLoading = false,
        statusMessage = null,
        statusIsSuccess = false,
        isLoggedIn = false,
        registrationSuccessSignal = 0,
        startInRegisterMode = true,
        onRegister = { _, _ -> },
        onAuthenticate = { _, _ -> },
    )
}

@Preview(
    name = "Ошибка входа",
    showSystemUi = true,
    device = Devices.PIXEL_4,
)
@Composable
private fun AuthScreenLoginErrorPreview() {
    AuthScreen(
        isLoading = false,
        statusMessage = "Неверное имя пользователя или пароль",
        statusIsSuccess = false,
        isLoggedIn = false,
        registrationSuccessSignal = 0,
        onRegister = { _, _ -> },
        onAuthenticate = { _, _ -> },
    )
}
