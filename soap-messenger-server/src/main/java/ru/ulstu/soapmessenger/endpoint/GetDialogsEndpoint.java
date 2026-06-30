package ru.ulstu.soapmessenger.endpoint;

import java.util.UUID;

import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import ru.ulstu.soapmessenger.service.DialogService;
import ru.ulstu.soapmessenger.soap.JwtAuthenticationInterceptor;
import ru.ulstu.soapmessenger.soap.generated.GetDialogsRequest;
import ru.ulstu.soapmessenger.soap.generated.GetDialogsResponse;

@Endpoint
public class GetDialogsEndpoint {

	private static final String NAMESPACE = "urn:soap-messenger:v1";

	private final DialogService dialogService;

	public GetDialogsEndpoint(DialogService dialogService) {
		this.dialogService = dialogService;
	}

	@PayloadRoot(namespace = NAMESPACE, localPart = "GetDialogsRequest")
	@ResponsePayload
	public GetDialogsResponse getDialogs(@RequestPayload GetDialogsRequest request, MessageContext messageContext) {
		UUID currentUserId = (UUID) messageContext.getProperty(JwtAuthenticationInterceptor.AUTHENTICATED_USER_ID);
		GetDialogsResponse response = new GetDialogsResponse();
		response.getDialog().addAll(dialogService.getDialogs(currentUserId));
		return response;
	}

}
