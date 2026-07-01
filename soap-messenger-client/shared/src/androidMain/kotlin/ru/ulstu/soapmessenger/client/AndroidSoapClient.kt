package ru.ulstu.soapmessenger.client

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader

sealed interface SoapCallResult<out T> {
    data class Ok<T>(val value: T) : SoapCallResult<T>
    data class Fault(val message: String) : SoapCallResult<Nothing>
    data object NetworkError : SoapCallResult<Nothing>
}

class AndroidSoapClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    fun registerUser(username: String, password: String): SoapCallResult<String> =
        executeSoap(buildAuthEnvelope("RegisterUserRequest", username, password)) { body ->
            parseSingleValueResponse(body, "userId")
        }

    fun authenticateUser(username: String, password: String): SoapCallResult<String> =
        executeSoap(buildAuthEnvelope("AuthenticateUserRequest", username, password)) { body ->
            parseSingleValueResponse(body, "token")
        }

    fun findUser(token: String, username: String): SoapCallResult<MessengerUser> =
        executeSoap(buildFindUserEnvelope(username), token) { body ->
            parseFindUserResponse(body)
        }

    fun openOrCreateDialog(token: String, otherUserId: String): SoapCallResult<DialogSummary> =
        executeSoap(buildOpenOrCreateDialogEnvelope(otherUserId), token) { body ->
            parseOpenOrCreateDialogResponse(body)
        }

    fun getDialogs(token: String): SoapCallResult<List<DialogSummary>> =
        executeSoap(buildGetDialogsEnvelope(), token) { body ->
            parseGetDialogsResponse(body)
        }

    private fun <T> executeSoap(
        envelope: String,
        token: String? = null,
        parseBody: (String) -> SoapCallResult<T>,
    ): SoapCallResult<T> {
        val requestBuilder = Request.Builder()
            .url(SOAP_ENDPOINT)
            .post(envelope.toRequestBody(CONTENT_TYPE))
        if (token != null) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        return try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    SoapCallResult.NetworkError
                } else {
                    parseBody(body)
                }
            }
        } catch (_: IOException) {
            SoapCallResult.NetworkError
        }
    }

    private fun buildAuthEnvelope(requestElement: String, username: String, password: String): String =
        """
        |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tns="urn:soap-messenger:v1">
        |  <soapenv:Body>
        |    <tns:$requestElement>
        |      <tns:username>${escapeXml(username)}</tns:username>
        |      <tns:password>${escapeXml(password)}</tns:password>
        |    </tns:$requestElement>
        |  </soapenv:Body>
        |</soapenv:Envelope>
        """.trimMargin()

    private fun buildFindUserEnvelope(username: String): String =
        """
        |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tns="urn:soap-messenger:v1">
        |  <soapenv:Body>
        |    <tns:FindUserRequest>
        |      <tns:username>${escapeXml(username)}</tns:username>
        |    </tns:FindUserRequest>
        |  </soapenv:Body>
        |</soapenv:Envelope>
        """.trimMargin()

    private fun buildOpenOrCreateDialogEnvelope(otherUserId: String): String =
        """
        |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tns="urn:soap-messenger:v1">
        |  <soapenv:Body>
        |    <tns:OpenOrCreateDialogRequest>
        |      <tns:otherUserId>${escapeXml(otherUserId)}</tns:otherUserId>
        |    </tns:OpenOrCreateDialogRequest>
        |  </soapenv:Body>
        |</soapenv:Envelope>
        """.trimMargin()

    private fun buildGetDialogsEnvelope(): String =
        """
        |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tns="urn:soap-messenger:v1">
        |  <soapenv:Body>
        |    <tns:GetDialogsRequest/>
        |  </soapenv:Body>
        |</soapenv:Envelope>
        """.trimMargin()

    private fun parseSingleValueResponse(body: String, valueElement: String): SoapCallResult<String> {
        val fault = parseSoapFault(body)
        if (fault != null) {
            return SoapCallResult.Fault(fault)
        }

        val parser = createParser(body)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG &&
                parser.namespace == TNS_NS &&
                parser.name == valueElement
            ) {
                return SoapCallResult.Ok(readElementText(parser))
            }
            event = parser.next()
        }
        return SoapCallResult.Fault("Не удалось разобрать ответ сервера")
    }

    private fun parseFindUserResponse(body: String): SoapCallResult<MessengerUser> {
        val fault = parseSoapFault(body)
        if (fault != null) {
            return SoapCallResult.Fault(fault)
        }

        val parser = createParser(body)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG &&
                parser.namespace == TNS_NS &&
                parser.name == "user"
            ) {
                return SoapCallResult.Ok(parseUser(parser))
            }
            event = parser.next()
        }
        return SoapCallResult.Fault("Не удалось разобрать ответ сервера")
    }

    private fun parseOpenOrCreateDialogResponse(body: String): SoapCallResult<DialogSummary> {
        val fault = parseSoapFault(body)
        if (fault != null) {
            return SoapCallResult.Fault(fault)
        }

        val parser = createParser(body)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG &&
                parser.namespace == TNS_NS &&
                parser.name == "dialog"
            ) {
                return SoapCallResult.Ok(parseDialogSummary(parser))
            }
            event = parser.next()
        }
        return SoapCallResult.Fault("Не удалось разобрать ответ сервера")
    }

    private fun parseGetDialogsResponse(body: String): SoapCallResult<List<DialogSummary>> {
        val fault = parseSoapFault(body)
        if (fault != null) {
            return SoapCallResult.Fault(fault)
        }

        val dialogs = mutableListOf<DialogSummary>()
        val parser = createParser(body)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG &&
                parser.namespace == TNS_NS &&
                parser.name == "dialog"
            ) {
                dialogs.add(parseDialogSummary(parser))
            }
            event = parser.next()
        }
        return SoapCallResult.Ok(dialogs)
    }

    private fun parseSoapFault(body: String): String? {
        val parser = createParser(body)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when {
                    parser.namespace == SOAP_NS && parser.name == "Fault" ->
                        return extractFaultMessage(parser)
                    parser.namespace == TNS_NS && parser.name.endsWith("Fault") ->
                        return parseServiceFault(parser)
                }
            }
            event = parser.next()
        }
        return null
    }

    private fun parseUser(parser: XmlPullParser): MessengerUser {
        var userId = ""
        var username = ""
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    when {
                        parser.namespace == TNS_NS && parser.name == "userId" -> {
                            userId = readElementText(parser)
                            depth--
                        }
                        parser.namespace == TNS_NS && parser.name == "username" -> {
                            username = readElementText(parser)
                            depth--
                        }
                    }
                }
                XmlPullParser.END_TAG -> depth--
            }
        }
        return MessengerUser(userId = userId, username = username)
    }

    private fun parseDialogSummary(parser: XmlPullParser): DialogSummary {
        var dialogId = ""
        var interlocutor = MessengerUser(userId = "", username = "")
        var lastMessageContent: String? = null
        var lastMessageCreatedAt: String? = null
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    when {
                        parser.namespace == TNS_NS && parser.name == "dialogId" -> {
                            dialogId = readElementText(parser)
                            depth--
                        }
                        parser.namespace == TNS_NS && parser.name == "interlocutor" -> {
                            interlocutor = parseUser(parser)
                        }
                        parser.namespace == TNS_NS && parser.name == "lastMessageContent" -> {
                            lastMessageContent = readElementText(parser)
                            depth--
                        }
                        parser.namespace == TNS_NS && parser.name == "lastMessageCreatedAt" -> {
                            lastMessageCreatedAt = readElementText(parser)
                            depth--
                        }
                    }
                }
                XmlPullParser.END_TAG -> depth--
            }
        }
        return DialogSummary(
            dialogId = dialogId,
            interlocutor = interlocutor,
            lastMessageContent = lastMessageContent,
            lastMessageCreatedAt = lastMessageCreatedAt,
        )
    }

    private fun extractFaultMessage(parser: XmlPullParser): String {
        var depth = 1
        var message: String? = null
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    if (parser.namespace == TNS_NS && parser.name == "message") {
                        message = readElementText(parser)
                        depth--
                    }
                }
                XmlPullParser.END_TAG -> depth--
            }
        }
        return message ?: "Ошибка сервера"
    }

    private fun parseServiceFault(parser: XmlPullParser): String {
        var depth = 1
        var message: String? = null
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    if (parser.namespace == TNS_NS && parser.name == "message") {
                        message = readElementText(parser)
                        depth--
                    }
                }
                XmlPullParser.END_TAG -> depth--
            }
        }
        return message ?: "Ошибка сервера"
    }

    private fun readElementText(parser: XmlPullParser): String {
        var text = ""
        if (parser.next() == XmlPullParser.TEXT) {
            text = parser.text
            parser.next()
        }
        return text
    }

    private fun createParser(body: String): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newPullParser().apply {
            setInput(StringReader(body))
        }
    }

    private fun escapeXml(value: String): String = buildString(value.length) {
        for (character in value) {
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(character)
            }
        }
    }

    private companion object {
        const val SOAP_ENDPOINT = "http://10.0.2.2:8080/ws"

        const val SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/"
        const val TNS_NS = "urn:soap-messenger:v1"

        val CONTENT_TYPE = "text/xml; charset=utf-8".toMediaType()
    }
}
