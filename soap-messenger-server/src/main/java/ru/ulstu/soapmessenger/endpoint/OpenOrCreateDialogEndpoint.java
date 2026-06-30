package ru.ulstu.soapmessenger.endpoint;

import java.util.UUID;

import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import ru.ulstu.soapmessenger.service.DialogService;
import ru.ulstu.soapmessenger.soap.JwtAuthenticationInterceptor;
import ru.ulstu.soapmessenger.soap.generated.OpenOrCreateDialogRequest;
import ru.ulstu.soapmessenger.soap.generated.OpenOrCreateDialogResponse;

@Endpoint
public class OpenOrCreateDialogEndpoint {

	private static final String NAMESPACE = "urn:soap-messenger:v1";

	private final DialogService dialogService;

	public OpenOrCreateDialogEndpoint(DialogService dialogService) {
		this.dialogService = dialogService;
	}

	@PayloadRoot(namespace = NAMESPACE, localPart = "OpenOrCreateDialogRequest")
	@ResponsePayload
	public OpenOrCreateDialogResponse openOrCreateDialog(@RequestPayload OpenOrCreateDialogRequest request,
			MessageContext messageContext) {
		UUID currentUserId = (UUID) messageContext.getProperty(JwtAuthenticationInterceptor.AUTHENTICATED_USER_ID);
		OpenOrCreateDialogResponse response = new OpenOrCreateDialogResponse();
		response.setDialog(dialogService.openOrCreateDialog(currentUserId, UUID.fromString(request.getOtherUserId())));
		return response;
	}

}
