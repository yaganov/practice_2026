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
        execute(
            requestElement = "RegisterUserRequest",
            valueElement = "userId",
            username = username,
            password = password,
        )

    fun authenticateUser(username: String, password: String): SoapCallResult<String> =
        execute(
            requestElement = "AuthenticateUserRequest",
            valueElement = "token",
            username = username,
            password = password,
        )

    private fun execute(
        requestElement: String,
        valueElement: String,
        username: String,
        password: String,
    ): SoapCallResult<String> {
        val envelope = buildEnvelope(requestElement, username, password)
        val request = Request.Builder()
            .url(SOAP_ENDPOINT)
            .post(envelope.toRequestBody(CONTENT_TYPE))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    SoapCallResult.NetworkError
                } else {
                    parseResponse(body, valueElement)
                }
            }
        } catch (_: IOException) {
            SoapCallResult.NetworkError
        }
    }

    private fun buildEnvelope(requestElement: String, username: String, password: String): String =
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

    private fun parseResponse(
        body: String,
        valueElement: String,
    ): SoapCallResult<String> {
        val parser = createParser(body)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when {
                    parser.namespace == SOAP_NS && parser.name == "Fault" ->
                        return SoapCallResult.Fault(extractFaultMessage(parser))
                    parser.namespace == TNS_NS && parser.name == valueElement ->
                        return SoapCallResult.Ok(readElementText(parser))
                    parser.namespace == TNS_NS &&
                        (parser.name == "RegisterUserFault" || parser.name == "AuthenticateUserFault") ->
                        return SoapCallResult.Fault(parseServiceFault(parser))
                }
            }
            event = parser.next()
        }
        return SoapCallResult.Fault("Не удалось разобрать ответ сервера")
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
