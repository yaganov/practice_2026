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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.xml.transform.StringSource;

import ru.ulstu.soapmessenger.exception.RegistrationValidationException;
import ru.ulstu.soapmessenger.exception.UsernameAlreadyExistsException;
import ru.ulstu.soapmessenger.service.UserService;
import ru.ulstu.soapmessenger.soap.RegisterUserFaultExceptionResolver;

@WebServiceServerTest(RegisterUserEndpoint.class)
@Import(RegisterUserFaultExceptionResolver.class)
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
		when(userService.register("alice", "SecretPass1")).thenReturn(userId);

		client.sendRequest(withSoapEnvelope(registerUserEnvelope("alice", "SecretPass1")))
				.andExpect(noFault())
				.andExpect(xpath("//tns:RegisterUserResponse/tns:userId", NAMESPACES).evaluatesTo(userId.toString()));
	}

	@Test
	void registerUser_usernameAlreadyExistsFault() throws Exception {
		when(userService.register("alice", "SecretPass1")).thenThrow(new UsernameAlreadyExistsException());

		client.sendRequest(withSoapEnvelope(registerUserEnvelope("alice", "SecretPass1")))
				.andExpect(clientOrSenderFault("Имя пользователя уже занято"))
				.andExpect(xpath("//soapenv:Fault/detail/tns:RegisterUserFault/tns:code", NAMESPACES)
						.evaluatesTo("USERNAME_ALREADY_EXISTS"))
				.andExpect(xpath("//soapenv:Fault/faultstring", NAMESPACES)
						.evaluatesTo("Имя пользователя уже занято"));
	}

	@Test
	void registerUser_usernameTooShortFault() throws Exception {
		when(userService.register(eq("abcd"), eq("SecretPass1")))
				.thenThrow(new RegistrationValidationException("Имя пользователя должно содержать не менее 5 символов"));

		client.sendRequest(withSoapEnvelope(registerUserEnvelope("abcd", "SecretPass1")))
				.andExpect(clientOrSenderFault("Имя пользователя должно содержать не менее 5 символов"))
				.andExpect(xpath("//soapenv:Fault/detail/tns:RegisterUserFault/tns:code", NAMESPACES)
						.evaluatesTo("VALIDATION_ERROR"))
				.andExpect(xpath("//soapenv:Fault/faultstring", NAMESPACES)
						.evaluatesTo("Имя пользователя должно содержать не менее 5 символов"));
	}

	@Test
	void registerUser_passwordTooShortFault() throws Exception {
		when(userService.register(eq("alice"), eq("1234567")))
				.thenThrow(new RegistrationValidationException("Пароль должен содержать не менее 8 символов"));

		client.sendRequest(withSoapEnvelope(registerUserEnvelope("alice", "1234567")))
				.andExpect(clientOrSenderFault("Пароль должен содержать не менее 8 символов"))
				.andExpect(xpath("//soapenv:Fault/detail/tns:RegisterUserFault/tns:code", NAMESPACES)
						.evaluatesTo("VALIDATION_ERROR"))
				.andExpect(xpath("//soapenv:Fault/faultstring", NAMESPACES)
						.evaluatesTo("Пароль должен содержать не менее 8 символов"));
	}

	@Test
	void registerUser_passwordTooLongFault() throws Exception {
		String longPassword = "p".repeat(33);
		when(userService.register(eq("alice"), eq(longPassword)))
				.thenThrow(new RegistrationValidationException("Пароль не должен быть длиннее 32 символов"));

		client.sendRequest(withSoapEnvelope(registerUserEnvelope("alice", longPassword)))
				.andExpect(clientOrSenderFault("Пароль не должен быть длиннее 32 символов"))
				.andExpect(xpath("//soapenv:Fault/detail/tns:RegisterUserFault/tns:code", NAMESPACES)
						.evaluatesTo("VALIDATION_ERROR"))
				.andExpect(xpath("//soapenv:Fault/faultstring", NAMESPACES)
						.evaluatesTo("Пароль не должен быть длиннее 32 символов"));
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

}
