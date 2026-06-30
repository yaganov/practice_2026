package ru.ulstu.soapmessenger.endpoint;

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
import ru.ulstu.soapmessenger.service.UserService;
import ru.ulstu.soapmessenger.soap.JwtAuthenticationInterceptor;
import ru.ulstu.soapmessenger.soap.SoapFaultExceptionResolver;
import ru.ulstu.soapmessenger.soap.generated.ServiceErrorCodeType;
import ru.ulstu.soapmessenger.soap.generated.UserType;
import static org.springframework.ws.test.server.RequestCreators.withSoapEnvelope;

@WebServiceServerTest(FindUserEndpoint.class)
@Import(FindUserEndpointSoapTest.TestConfig.class)
class FindUserEndpointSoapTest {

	private static final Map<String, String> NAMESPACES = Map.of("soapenv", "http://schemas.xmlsoap.org/soap/envelope/",
			"tns", "urn:soap-messenger:v1");

	@Autowired
	private MockWebServiceClient client;

	@Autowired
	private JwtEncoder jwtEncoder;

	@MockitoBean
	private UserService userService;

	@AfterEach
	void tearDown() {
		TransportContextHolder.setTransportContext(null);
	}

	@Test
	void findUser_success() throws Exception {
		UUID userId = UUID.randomUUID();
		UserType userType = new UserType();
		userType.setUserId(userId.toString());
		userType.setUsername("alice");
		when(userService.findUser("alice")).thenReturn(userType);

		withBearerToken(createToken(userId));
		client.sendRequest(withSoapEnvelope(findUserEnvelope("alice")))
				.andExpect(noFault())
				.andExpect(xpath("//tns:FindUserResponse/tns:user/tns:userId", NAMESPACES)
						.evaluatesTo(userId.toString()))
				.andExpect(xpath("//tns:FindUserResponse/tns:user/tns:username", NAMESPACES).evaluatesTo("alice"));
	}

	@Test
	void findUser_unauthorizedFault() throws Exception {
		client.sendRequest(withSoapEnvelope(findUserEnvelope("alice")))
				.andExpect(clientOrSenderFault("Требуется действующий токен авторизации"))
				.andExpect(xpath("//soapenv:Fault/detail/tns:FindUserFault/tns:code", NAMESPACES)
						.evaluatesTo("UNAUTHORIZED"));
	}

	@Test
	void findUser_userNotFoundFault() throws Exception {
		when(userService.findUser("alice"))
				.thenThrow(new ServiceException(ServiceErrorCodeType.USER_NOT_FOUND, "Пользователь не найден"));

		withBearerToken(createToken(UUID.randomUUID()));
		client.sendRequest(withSoapEnvelope(findUserEnvelope("alice")))
				.andExpect(clientOrSenderFault("Пользователь не найден"))
				.andExpect(xpath("//soapenv:Fault/detail/tns:FindUserFault/tns:code", NAMESPACES)
						.evaluatesTo("USER_NOT_FOUND"));
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

	private Source findUserEnvelope(String username) {
		return new StringSource("""
				<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tns="urn:soap-messenger:v1">
				  <soapenv:Body>
				    <tns:FindUserRequest>
				      <tns:username>%s</tns:username>
				    </tns:FindUserRequest>
				  </soapenv:Body>
				</soapenv:Envelope>
				""".formatted(username));
	}

	@Configuration
	@Import({FindUserEndpoint.class, SecurityConfig.class})
	static class TestConfig {

		@Bean
		JwtAuthenticationInterceptor jwtAuthenticationInterceptor(JwtDecoder jwtDecoder) {
			return new JwtAuthenticationInterceptor(jwtDecoder);
		}

		@Bean
		PayloadRootSmartSoapEndpointInterceptor findUserJwtAuthenticationInterceptor(
				JwtAuthenticationInterceptor jwtAuthenticationInterceptor) {
			return new PayloadRootSmartSoapEndpointInterceptor(jwtAuthenticationInterceptor,
					"urn:soap-messenger:v1", "FindUserRequest");
		}

		@Bean
		SoapFaultExceptionResolver findUserFaultExceptionResolver(FindUserEndpoint findUserEndpoint) {
			return new SoapFaultExceptionResolver(findUserEndpoint, SoapFaultExceptionResolver.FAULT_DETAIL_FIND_USER);
		}

	}

}
