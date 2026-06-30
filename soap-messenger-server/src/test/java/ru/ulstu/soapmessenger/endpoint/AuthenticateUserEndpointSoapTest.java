package ru.ulstu.soapmessenger.endpoint;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.ws.test.server.RequestCreators.withSoapEnvelope;
import static org.springframework.ws.test.server.ResponseMatchers.clientOrSenderFault;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

import java.util.Map;

import javax.xml.transform.Source;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webservices.test.autoconfigure.server.WebServiceServerTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.xml.transform.StringSource;

import ru.ulstu.soapmessenger.exception.ServiceException;
import ru.ulstu.soapmessenger.service.UserService;
import ru.ulstu.soapmessenger.soap.SoapFaultExceptionResolver;
import ru.ulstu.soapmessenger.soap.generated.ServiceErrorCodeType;

@WebServiceServerTest(AuthenticateUserEndpoint.class)
@Import(AuthenticateUserEndpointSoapTest.SoapFaultConfig.class)
class AuthenticateUserEndpointSoapTest {

	private static final Map<String, String> NAMESPACES = Map.of("soapenv", "http://schemas.xmlsoap.org/soap/envelope/",
			"tns", "urn:soap-messenger:v1");

	@Autowired
	private MockWebServiceClient client;

	@MockitoBean
	private UserService userService;

	@Test
	void authenticateUser_success() throws Exception {
		when(userService.authenticateUser("alice", "SecretPass1")).thenReturn("jwt-token-value");

		client.sendRequest(withSoapEnvelope(authenticateUserEnvelope("alice", "SecretPass1")))
				.andExpect(noFault())
				.andExpect(xpath("//tns:AuthenticateUserResponse/tns:token", NAMESPACES)
						.evaluatesTo("jwt-token-value"));
	}

	@Test
	void authenticateUser_validationFault() throws Exception {
		when(userService.authenticateUser(eq("alice"), eq("1234567")))
				.thenThrow(new ServiceException(ServiceErrorCodeType.VALIDATION_ERROR,
						UserService.passwordTooShortMessage()));

		client.sendRequest(withSoapEnvelope(authenticateUserEnvelope("alice", "1234567")))
				.andExpect(clientOrSenderFault(UserService.passwordTooShortMessage()))
				.andExpect(xpath("//soapenv:Fault/detail/tns:AuthenticateUserFault/tns:code", NAMESPACES)
						.evaluatesTo("VALIDATION_ERROR"));
	}

	@Test
	void authenticateUser_invalidCredentialsFault() throws Exception {
		when(userService.authenticateUser("alice", "SecretPass1"))
				.thenThrow(new ServiceException(ServiceErrorCodeType.INVALID_CREDENTIALS,
						"Неверное имя пользователя или пароль"));

		client.sendRequest(withSoapEnvelope(authenticateUserEnvelope("alice", "SecretPass1")))
				.andExpect(clientOrSenderFault("Неверное имя пользователя или пароль"))
				.andExpect(xpath("//soapenv:Fault/detail/tns:AuthenticateUserFault/tns:code", NAMESPACES)
						.evaluatesTo("INVALID_CREDENTIALS"));
	}

	private Source authenticateUserEnvelope(String username, String password) {
		return new StringSource("""
				<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tns="urn:soap-messenger:v1">
				  <soapenv:Body>
				    <tns:AuthenticateUserRequest>
				      <tns:username>%s</tns:username>
				      <tns:password>%s</tns:password>
				    </tns:AuthenticateUserRequest>
				  </soapenv:Body>
				</soapenv:Envelope>
				""".formatted(username, password));
	}

	@Configuration
	@Import(AuthenticateUserEndpoint.class)
	static class SoapFaultConfig {

		@Bean
		SoapFaultExceptionResolver authenticateUserFaultExceptionResolver() {
			return new SoapFaultExceptionResolver(null, true);
		}

	}

}
