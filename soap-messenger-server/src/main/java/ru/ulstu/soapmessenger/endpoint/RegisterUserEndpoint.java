package ru.ulstu.soapmessenger.endpoint;

import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import ru.ulstu.soapmessenger.service.UserService;
import ru.ulstu.soapmessenger.soap.generated.RegisterUserRequest;
import ru.ulstu.soapmessenger.soap.generated.RegisterUserResponse;

@Endpoint
public class RegisterUserEndpoint {

	private static final String NAMESPACE = "urn:soap-messenger:v1";

	private final UserService userService;

	public RegisterUserEndpoint(UserService userService) {
		this.userService = userService;
	}

	@PayloadRoot(namespace = NAMESPACE, localPart = "RegisterUserRequest")
	@ResponsePayload
	public RegisterUserResponse registerUser(@RequestPayload RegisterUserRequest request) {
		RegisterUserResponse response = new RegisterUserResponse();
		response.setUserId(userService.registerUser(request.getUsername(), request.getPassword()).toString());
		return response;
	}

}
