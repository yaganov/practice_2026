package ru.ulstu.soapmessenger.endpoint;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.ws.test.server.ResponseMatchers.clientOrSenderFault;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.transform.Source;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webservices.test.autoconfigure.server.WebServiceServerTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.ws.soap.server.endpoint.interceptor.PayloadRootSmartSoapEndpointInterceptor;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.ws.transport.context.DefaultTransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;
import org.springframework.xml.transform.StringSource;

import ru.ulstu.soapmessenger.config.SecurityConfig;
import ru.ulstu.soapmessenger.exception.ServiceException;
import ru.ulstu.soapmessenger.service.DialogService;
import ru.ulstu.soapmessenger.soap.JwtAuthenticationInterceptor;
import ru.ulstu.soapmessenger.soap.SoapFaultExceptionResolver;
import ru.ulstu.soapmessenger.soap.generated.MessageType;
import ru.ulstu.soapmessenger.soap.generated.ServiceErrorCodeType;
import static org.springframework.ws.test.server.RequestCreators.withSoapEnvelope;

@WebServiceServerTest(GetMessageHistoryEndpoint.class)
@Import(GetMessageHistoryEndpointSoapTest.TestConfig.class)
class GetMessageHistoryEndpointSoapTest {

	private static final Map<String, String> NAMESPACES = Map.of("soapenv", "http://schemas.xmlsoap.org/soap/envelope/",
			"tns", "urn:soap-messenger:v1");

	@Autowired
	private MockWebServiceClient client;

	@Autowired
	private JwtEncoder jwtEncoder;

	@MockitoBean
	private DialogService dialogService;

	@AfterEach
	void tearDown() {
		TransportContextHolder.setTransportContext(null);
	}

	@Test
	void getMessageHistory_success() throws Exception {
		UUID currentUserId = UUID.randomUUID();
		UUID dialogId = UUID.randomUUID();
		UUID messageId = UUID.randomUUID();
		UUID senderId = UUID.randomUUID();

		MessageType message = new MessageType();
		message.setMessageId(messageId.toString());
		message.setSenderId(senderId.toString());
		message.setContent("hello");

		when(dialogService.getMessageHistory(eq(currentUserId), eq(dialogId))).thenReturn(List.of(message));

		withBearerToken(createToken(currentUserId));
		client.sendRequest(withSoapEnvelope(getMessageHistoryEnvelope(dialogId)))
				.andExpect(noFault())
				.andExpect(xpath("//tns:GetMessageHistoryResponse/tns:message/tns:messageId", NAMESPACES)
						.evaluatesTo(messageId.toString()))
				.andExpect(xpath("//tns:GetMessageHistoryResponse/tns:message/tns:senderId", NAMESPACES)
						.evaluatesTo(senderId.toString()))
				.andExpect(xpath("//tns:GetMessageHistoryResponse/tns:message/tns:content", NAMESPACES)
						.evaluatesTo("hello"));
	}

	@Test
	void getMessageHistory_unauthorizedFault() throws Exception {
		client.sendRequest(withSoapEnvelope(getMessageHistoryEnvelope(UUID.randomUUID())))
				.andExpect(clientOrSenderFault("Требуется действующий токен авторизации"))
				.andExpect(xpath("//soapenv:Fault/detail/tns:GetMessageHistoryFault/tns:code", NAMESPACES)
						.evaluatesTo("UNAUTHORIZED"));
	}

	@Test
	void getMessageHistory_accessDeniedFault() throws Exception {
		UUID currentUserId = UUID.randomUUID();
		UUID dialogId = UUID.randomUUID();

		when(dialogService.getMessageHistory(eq(currentUserId), eq(dialogId)))
				.thenThrow(new ServiceException(ServiceErrorCodeType.ACCESS_DENIED, "Нет доступа к диалогу"));

		withBearerToken(createToken(currentUserId));
		client.sendRequest(withSoapEnvelope(getMessageHistoryEnvelope(dialogId)))
				.andExpect(clientOrSenderFault("Нет доступа к диалогу"))
				.andExpect(xpath("//soapenv:Fault/detail/tns:GetMessageHistoryFault/tns:code", NAMESPACES)
						.evaluatesTo("ACCESS_DENIED"));
	}

	private void withBearerToken(String token) throws Exception {
		HttpServletConnection connection = mock(HttpServletConnection.class);
		when(connection.getRequestHeaders("Authorization")).thenReturn(List.of("Bearer " + token).iterator());
		TransportContextHolder.setTransportContext(new DefaultTransportContext(connection));
	}

	private String createToken(UUID userId) {
		Instant issuedAt = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(SecurityConfig.JWT_ISSUER)
				.subject(userId.toString())
				.issuedAt(issuedAt)
				.expiresAt(issuedAt.plus(Duration.ofHours(1)))
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private Source getMessageHistoryEnvelope(UUID dialogId) {
		return new StringSource("""
				<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tns="urn:soap-messenger:v1">
				  <soapenv:Body>
				    <tns:GetMessageHistoryRequest>
				      <tns:dialogId>%s</tns:dialogId>
				    </tns:GetMessageHistoryRequest>
				  </soapenv:Body>
				</soapenv:Envelope>
				""".formatted(dialogId));
	}

	@Configuration
	@Import({GetMessageHistoryEndpoint.class, SecurityConfig.class})
	static class TestConfig {

		@Bean
		JwtAuthenticationInterceptor jwtAuthenticationInterceptor(JwtDecoder jwtDecoder) {
			return new JwtAuthenticationInterceptor(jwtDecoder);
		}

		@Bean
		PayloadRootSmartSoapEndpointInterceptor getMessageHistoryJwtAuthenticationInterceptor(
				JwtAuthenticationInterceptor jwtAuthenticationInterceptor) {
			return new PayloadRootSmartSoapEndpointInterceptor(jwtAuthenticationInterceptor,
					"urn:soap-messenger:v1", "GetMessageHistoryRequest");
		}

		@Bean
		SoapFaultExceptionResolver getMessageHistoryFaultExceptionResolver(
				GetMessageHistoryEndpoint getMessageHistoryEndpoint) {
			return new SoapFaultExceptionResolver(getMessageHistoryEndpoint,
					SoapFaultExceptionResolver.FAULT_DETAIL_GET_MESSAGE_HISTORY);
		}

	}

}
