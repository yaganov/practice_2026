package ru.ulstu.soapmessenger.endpoint;

import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import ru.ulstu.soapmessenger.service.UserService;
import ru.ulstu.soapmessenger.soap.generated.AuthenticateUserRequest;
import ru.ulstu.soapmessenger.soap.generated.AuthenticateUserResponse;

@Endpoint
public class AuthenticateUserEndpoint {

	private static final String NAMESPACE = "urn:soap-messenger:v1";

	private final UserService userService;

	public AuthenticateUserEndpoint(UserService userService) {
		this.userService = userService;
	}

	@PayloadRoot(namespace = NAMESPACE, localPart = "AuthenticateUserRequest")
	@ResponsePayload
	public AuthenticateUserResponse authenticateUser(@RequestPayload AuthenticateUserRequest request) {
		AuthenticateUserResponse response = new AuthenticateUserResponse();
		response.setToken(userService.authenticateUser(request.getUsername(), request.getPassword()));
		return response;
	}

}
