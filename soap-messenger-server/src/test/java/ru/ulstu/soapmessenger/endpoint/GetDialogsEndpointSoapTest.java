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
import ru.ulstu.soapmessenger.service.DialogService;
import ru.ulstu.soapmessenger.soap.JwtAuthenticationInterceptor;
import ru.ulstu.soapmessenger.soap.SoapFaultExceptionResolver;
import ru.ulstu.soapmessenger.soap.generated.DialogSummaryType;
import ru.ulstu.soapmessenger.soap.generated.UserType;
import static org.springframework.ws.test.server.RequestCreators.withSoapEnvelope;

@WebServiceServerTest(GetDialogsEndpoint.class)
@Import(GetDialogsEndpointSoapTest.TestConfig.class)
class GetDialogsEndpointSoapTest {

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
	void getDialogs_success() throws Exception {
		UUID currentUserId = UUID.randomUUID();
		UUID dialogId = UUID.randomUUID();
		UUID otherUserId = UUID.randomUUID();

		UserType interlocutor = new UserType();
		interlocutor.setUserId(otherUserId.toString());
		interlocutor.setUsername("bob");

		DialogSummaryType dialogSummary = new DialogSummaryType();
		dialogSummary.setDialogId(dialogId.toString());
		dialogSummary.setInterlocutor(interlocutor);

		when(dialogService.getDialogs(eq(currentUserId))).thenReturn(List.of(dialogSummary));

		withBearerToken(createToken(currentUserId));
		client.sendRequest(withSoapEnvelope(getDialogsEnvelope()))
				.andExpect(noFault())
				.andExpect(xpath("//tns:GetDialogsResponse/tns:dialog/tns:dialogId", NAMESPACES)
						.evaluatesTo(dialogId.toString()))
				.andExpect(xpath("//tns:GetDialogsResponse/tns:dialog/tns:interlocutor/tns:userId", NAMESPACES)
						.evaluatesTo(otherUserId.toString()))
				.andExpect(xpath("//tns:GetDialogsResponse/tns:dialog/tns:interlocutor/tns:username", NAMESPACES)
						.evaluatesTo("bob"));
	}

	@Test
	void getDialogs_unauthorizedFault() throws Exception {
		client.sendRequest(withSoapEnvelope(getDialogsEnvelope()))
				.andExpect(clientOrSenderFault("Требуется действующий токен авторизации"))
				.andExpect(xpath("//soapenv:Fault/detail/tns:GetDialogsFault/tns:code", NAMESPACES)
						.evaluatesTo("UNAUTHORIZED"));
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

	private Source getDialogsEnvelope() {
		return new StringSource("""
				<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tns="urn:soap-messenger:v1">
				  <soapenv:Body>
				    <tns:GetDialogsRequest/>
				  </soapenv:Body>
				</soapenv:Envelope>
				""");
	}

	@Configuration
	@Import({GetDialogsEndpoint.class, SecurityConfig.class})
	static class TestConfig {

		@Bean
		JwtAuthenticationInterceptor jwtAuthenticationInterceptor(JwtDecoder jwtDecoder) {
			return new JwtAuthenticationInterceptor(jwtDecoder);
		}

		@Bean
		PayloadRootSmartSoapEndpointInterceptor getDialogsJwtAuthenticationInterceptor(
				JwtAuthenticationInterceptor jwtAuthenticationInterceptor) {
			return new PayloadRootSmartSoapEndpointInterceptor(jwtAuthenticationInterceptor,
					"urn:soap-messenger:v1", "GetDialogsRequest");
		}

		@Bean
		SoapFaultExceptionResolver getDialogsFaultExceptionResolver(GetDialogsEndpoint getDialogsEndpoint) {
			return new SoapFaultExceptionResolver(getDialogsEndpoint, SoapFaultExceptionResolver.FAULT_DETAIL_GET_DIALOGS);
		}

	}

}
