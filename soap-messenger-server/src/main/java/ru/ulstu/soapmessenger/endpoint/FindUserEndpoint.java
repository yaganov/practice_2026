package ru.ulstu.soapmessenger.endpoint;

import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import ru.ulstu.soapmessenger.service.UserService;
import ru.ulstu.soapmessenger.soap.generated.FindUserRequest;
import ru.ulstu.soapmessenger.soap.generated.FindUserResponse;

@Endpoint
public class FindUserEndpoint {

	private static final String NAMESPACE = "urn:soap-messenger:v1";

	private final UserService userService;

	public FindUserEndpoint(UserService userService) {
		this.userService = userService;
	}

	@PayloadRoot(namespace = NAMESPACE, localPart = "FindUserRequest")
	@ResponsePayload
	public FindUserResponse findUser(@RequestPayload FindUserRequest request) {
		FindUserResponse response = new FindUserResponse();
		response.setUser(userService.findUser(request.getUsername()));
		return response;
	}

}
