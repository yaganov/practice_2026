package ru.ulstu.soapmessenger.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {

    private val jwtToken = mutableStateOf<String?>(null)
    private val soapClient = AndroidSoapClient()

    private val isLoading = mutableStateOf(false)
    private val statusMessage = mutableStateOf<String?>(null)
    private val statusIsSuccess = mutableStateOf(false)
    private val isLoggedIn = mutableStateOf(false)
    private val registrationSuccessSignal = mutableStateOf(0)

    private val dialogs = mutableStateOf<List<DialogSummary>>(emptyList())
    private val foundUser = mutableStateOf<MessengerUser?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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
                                    updateOnMain {
                                        jwtToken.value = tokenValue
                                        isLoggedIn.value = true
                                        statusMessage.value = null
                                        foundUser.value = null
                                    }
                                    applyGetDialogsResult(soapClient.getDialogs(tokenValue))
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
                    onOpenDialog = { otherUserId -> openDialog(otherUserId) },
                    onSearchQueryChanged = { onSearchQueryChanged() },
                )
            }
        }
    }

    private fun onSearchQueryChanged() {
        foundUser.value = null
        if (statusIsSuccess.value) {
            statusMessage.value = null
        }
    }

    private fun refreshDialogs() {
        val token = jwtToken.value ?: return
        runSoapCall {
            applyGetDialogsResult(soapClient.getDialogs(token))
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

    private fun openDialog(otherUserId: String) {
        val token = jwtToken.value ?: return
        runSoapCall {
            when (val result = soapClient.openOrCreateDialog(token, otherUserId)) {
                is SoapCallResult.Ok ->
                    applyGetDialogsResult(soapClient.getDialogs(token), showOpenedMessage = true)
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
        result: SoapCallResult<List<DialogSummary>>,
        showOpenedMessage: Boolean = false,
    ) {
        when (result) {
            is SoapCallResult.Ok -> updateOnMain {
                dialogs.value = result.value
                if (showOpenedMessage) {
                    foundUser.value = null
                    statusIsSuccess.value = true
                    statusMessage.value = "Диалог открыт"
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

    private fun runSoapCall(block: () -> Unit) {
        isLoading.value = true
        Thread {
            try {
                block()
            } finally {
                updateOnMain { isLoading.value = false }
            }
        }.start()
    }

    private fun updateOnMain(block: () -> Unit) {
        runOnUiThread(block)
    }

    private companion object {
        const val NETWORK_ERROR_MESSAGE = "Не удалось выполнить запрос. Проверьте подключение к серверу."
    }
}
