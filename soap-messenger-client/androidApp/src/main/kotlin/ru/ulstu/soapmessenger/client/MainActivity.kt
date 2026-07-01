package ru.ulstu.soapmessenger.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    private var jwtToken: String? = null
    private val soapClient = AndroidSoapClient()

    private val isLoading = mutableStateOf(false)
    private val statusMessage = mutableStateOf<String?>(null)
    private val statusIsSuccess = mutableStateOf(false)
    private val isLoggedIn = mutableStateOf(false)
    private val registrationSuccessSignal = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
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
                            is SoapCallResult.Ok -> {
                                statusIsSuccess.value = true
                                registrationSuccessSignal.value += 1
                                statusMessage.value = "Регистрация выполнена. Войдите в аккаунт."
                            }
                            is SoapCallResult.Fault -> {
                                statusIsSuccess.value = false
                                statusMessage.value = result.message
                            }
                            SoapCallResult.NetworkError -> {
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
                                jwtToken = result.value
                                isLoggedIn.value = true
                                statusIsSuccess.value = true
                                statusMessage.value = "Вход выполнен"
                            }
                            is SoapCallResult.Fault -> {
                                statusIsSuccess.value = false
                                statusMessage.value = result.message
                            }
                            SoapCallResult.NetworkError -> {
                                statusIsSuccess.value = false
                                statusMessage.value = "Ошибка сети. Проверьте подключение."
                            }
                        }
                    }
                },
            )
        }
    }

    private fun runSoapCall(block: () -> Unit) {
        isLoading.value = true
        Thread {
            try {
                block()
            } finally {
                runOnUiThread { isLoading.value = false }
            }
        }.start()
    }
}
