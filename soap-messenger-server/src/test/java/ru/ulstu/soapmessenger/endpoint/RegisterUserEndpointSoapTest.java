package ru.ulstu.soapmessenger.endpoint;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.ws.test.server.RequestCreators.withSoapEnvelope;
import static org.springframework.ws.test.server.ResponseMatchers.clientOrSenderFault;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

import java.util.Map;
import java.util.UUID;

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

@WebServiceServerTest(RegisterUserEndpoint.class)
@Import(RegisterUserEndpointSoapTest.SoapFaultConfig.class)
class RegisterUserEndpointSoapTest {

	private static final Map<String, String> NAMESPACES = Map.of("soapenv", "http://schemas.xmlsoap.org/soap/envelope/",
			"tns", "urn:soap-messenger:v1");

	@Autowired
	private MockWebServiceClient client;

	@MockitoBean
	private UserService userService;

	@Test
	void registerUser_success() throws Exception {
		UUID userId = UUID.randomUUID();
		when(userService.registerUser("alice", "SecretPass1")).thenReturn(userId);

		client.sendRequest(withSoapEnvelope(registerUserEnvelope("alice", "SecretPass1")))
				.andExpect(noFault())
				.andExpect(xpath("//tns:RegisterUserResponse/tns:userId", NAMESPACES).evaluatesTo(userId.toString()));
	}

	@Test
	void registerUser_validationFault() throws Exception {
		when(userService.registerUser(eq("abcd"), eq("SecretPass1")))
				.thenThrow(new ServiceException(ServiceErrorCodeType.VALIDATION_ERROR,
						UserService.usernameTooShortMessage()));

		client.sendRequest(withSoapEnvelope(registerUserEnvelope("abcd", "SecretPass1")))
				.andExpect(clientOrSenderFault(UserService.usernameTooShortMessage()))
				.andExpect(xpath("//soapenv:Fault/detail/tns:RegisterUserFault/tns:code", NAMESPACES)
						.evaluatesTo("VALIDATION_ERROR"));
	}

	@Test
	void registerUser_usernameAlreadyExistsFault() throws Exception {
		when(userService.registerUser("alice", "SecretPass1"))
				.thenThrow(new ServiceException(ServiceErrorCodeType.USERNAME_ALREADY_EXISTS,
						"Имя пользователя уже занято"));

		client.sendRequest(withSoapEnvelope(registerUserEnvelope("alice", "SecretPass1")))
				.andExpect(clientOrSenderFault("Имя пользователя уже занято"))
				.andExpect(xpath("//soapenv:Fault/detail/tns:RegisterUserFault/tns:code", NAMESPACES)
						.evaluatesTo("USERNAME_ALREADY_EXISTS"));
	}

	private Source registerUserEnvelope(String username, String password) {
		return new StringSource("""
				<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tns="urn:soap-messenger:v1">
				  <soapenv:Body>
				    <tns:RegisterUserRequest>
				      <tns:username>%s</tns:username>
				      <tns:password>%s</tns:password>
				    </tns:RegisterUserRequest>
				  </soapenv:Body>
				</soapenv:Envelope>
				""".formatted(username, password));
	}

	@Configuration
	@Import(RegisterUserEndpoint.class)
	static class SoapFaultConfig {

		@Bean
		SoapFaultExceptionResolver registerUserFaultExceptionResolver(RegisterUserEndpoint registerUserEndpoint) {
			return new SoapFaultExceptionResolver(registerUserEndpoint, false);
		}

	}

}
