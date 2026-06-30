package ru.ulstu.soapmessenger.endpoint;

import java.util.UUID;

import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import ru.ulstu.soapmessenger.service.DialogService;
import ru.ulstu.soapmessenger.soap.JwtAuthenticationInterceptor;
import ru.ulstu.soapmessenger.soap.generated.GetMessageHistoryRequest;
import ru.ulstu.soapmessenger.soap.generated.GetMessageHistoryResponse;

@Endpoint
public class GetMessageHistoryEndpoint {

	private static final String NAMESPACE = "urn:soap-messenger:v1";

	private final DialogService dialogService;

	public GetMessageHistoryEndpoint(DialogService dialogService) {
		this.dialogService = dialogService;
	}

	@PayloadRoot(namespace = NAMESPACE, localPart = "GetMessageHistoryRequest")
	@ResponsePayload
	public GetMessageHistoryResponse getMessageHistory(@RequestPayload GetMessageHistoryRequest request,
			MessageContext messageContext) {
		UUID currentUserId = (UUID) messageContext.getProperty(JwtAuthenticationInterceptor.AUTHENTICATED_USER_ID);
		GetMessageHistoryResponse response = new GetMessageHistoryResponse();
		response.getMessage().addAll(dialogService.getMessageHistory(currentUserId,
				UUID.fromString(request.getDialogId())));
		return response;
	}

}
