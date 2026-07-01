package ru.ulstu.soapmessenger.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ScreenBackground = Color(0xFFF5F6F8)
private val GraphiteText = Color(0xFF1F2933)
private val MutedText = Color(0xFF52606D)
private val AccentBlue = Color(0xFF1B3A5C)
private val FieldBorder = Color(0xFFCBD2D9)
private val StatusErrorBackground = Color(0xFFFBEAEA)
private val StatusErrorText = Color(0xFF9B1C1C)
private val StatusSuccessBackground = Color(0xFFE8F5E9)
private val StatusSuccessText = Color(0xFF1B5E20)

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
                .background(ScreenBackground)
                .safeContentPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "SOAP Messenger",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                ),
                color = AccentBlue,
            )

            Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(4.dp))

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
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue,
                    contentColor = Color.White,
                    disabledContainerColor = AccentBlue.copy(alpha = 0.4f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f),
                ),
            ) {
                Text(
                    text = if (mode == AuthMode.Login) "Войти" else "Зарегистрироваться",
                    fontWeight = FontWeight.Medium,
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
                    color = AccentBlue,
                    textAlign = TextAlign.Center,
                )
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.CenterHorizontally),
                    color = AccentBlue,
                    strokeWidth = 2.dp,
                )
            }

            statusMessage?.let { message ->
                StatusMessageBlock(
                    message = message,
                    isSuccess = isLoggedIn || statusIsSuccess,
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
            color = MutedText,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            enabled = enabled,
            visualTransformation = visualTransformation,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(8.dp),
            colors = authFieldColors(),
        )
    }
}

@Composable
private fun AuthHeader(mode: AuthMode) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (mode == AuthMode.Login) "Вход" else "Создание аккаунта",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = GraphiteText,
        )
        Text(
            text = if (mode == AuthMode.Login) {
                "Введите данные учётной записи"
            } else {
                "Создайте учётную запись для начала общения"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
        )
    }
}

@Composable
private fun StatusMessageBlock(message: String, isSuccess: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isSuccess) StatusSuccessBackground else StatusErrorBackground,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSuccess) StatusSuccessText else StatusErrorText,
        )
    }
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = FieldBorder,
    disabledBorderColor = FieldBorder.copy(alpha = 0.6f),
    cursorColor = AccentBlue,
    focusedTextColor = GraphiteText,
    unfocusedTextColor = GraphiteText,
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
