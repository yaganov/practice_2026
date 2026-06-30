package ru.ulstu.soapmessenger.soap;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import ru.ulstu.soapmessenger.exception.ServiceException;
import ru.ulstu.soapmessenger.model.Message;
import ru.ulstu.soapmessenger.repository.DialogRepository;
import ru.ulstu.soapmessenger.service.DialogService;
import ru.ulstu.soapmessenger.soap.generated.ServiceErrorCodeType;

@Component
public class MessageWebSocketHandler extends TextWebSocketHandler {

	private static final String SEND_MESSAGE_TYPE = "sendMessage";
	private static final String MESSAGE_TYPE = "message";
	private static final String ERROR_TYPE = "error";
	private static final String INTERNAL_ERROR_MESSAGE = "Внутренняя ошибка сервера";
	private static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	private final DialogService dialogService;
	private final DialogRepository dialogRepository;
	private final ObjectMapper objectMapper;
	private final Map<UUID, Set<WebSocketSession>> sessionsByUserId = new ConcurrentHashMap<>();

	public MessageWebSocketHandler(DialogService dialogService, DialogRepository dialogRepository,
			ObjectMapper objectMapper) {
		this.dialogService = dialogService;
		this.dialogRepository = dialogRepository;
		this.objectMapper = objectMapper;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		UUID userId = getAuthenticatedUserId(session);
		sessionsByUserId.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		removeSession(getAuthenticatedUserId(session), session);
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		try {
			JsonNode payload = objectMapper.readTree(message.getPayload());
			if (!SEND_MESSAGE_TYPE.equals(payload.path("type").asText())) {
				return;
			}
			UUID userId = getAuthenticatedUserId(session);
			UUID dialogId = UUID.fromString(payload.path("dialogId").asText());
			UUID clientMessageId = UUID.fromString(payload.path("clientMessageId").asText());
			String content = payload.path("content").asText();

			Message savedMessage = dialogService.sendMessage(userId, dialogId, clientMessageId, content);
			broadcastMessage(savedMessage);
		}
		catch (ServiceException ex) {
			sendError(session, ex.getCode().value(), ex.getMessage());
		}
		catch (Exception ex) {
			sendError(session, ServiceErrorCodeType.INTERNAL_ERROR.value(), INTERNAL_ERROR_MESSAGE);
		}
	}

	private void broadcastMessage(Message savedMessage) {
		try {
			String eventJson = objectMapper.writeValueAsString(toMessageEvent(savedMessage));
			for (UUID participantId : dialogRepository.findParticipantIdsByDialogId(savedMessage.getDialogId())) {
				sendToUser(participantId, eventJson);
			}
		}
		catch (Exception ex) {
			// delivery failure after successful save must not surface as client error
		}
	}

	private void sendToUser(UUID userId, String payload) {
		Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
		if (sessions == null) {
			return;
		}
		for (WebSocketSession session : sessions) {
			sendToSession(userId, session, payload);
		}
	}

	private void sendToSession(UUID userId, WebSocketSession session, String payload) {
		synchronized (session) {
			if (!session.isOpen()) {
				removeSession(userId, session);
				return;
			}
			try {
				session.sendMessage(new TextMessage(payload));
			}
			catch (IOException ex) {
				removeSession(userId, session);
			}
		}
	}

	private void sendError(WebSocketSession session, String code, String errorMessage) {
		try {
			String payload = objectMapper.writeValueAsString(new ErrorEvent(ERROR_TYPE, code, errorMessage));
			sendToSession(getAuthenticatedUserId(session), session, payload);
		}
		catch (Exception ex) {
			removeSession(getAuthenticatedUserId(session), session);
		}
	}

	private void removeSession(UUID userId, WebSocketSession session) {
		Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
		if (sessions == null) {
			return;
		}
		sessions.remove(session);
		if (sessions.isEmpty()) {
			sessionsByUserId.remove(userId, sessions);
		}
	}

	private static UUID getAuthenticatedUserId(WebSocketSession session) {
		return (UUID) session.getAttributes().get(JwtAuthenticationInterceptor.AUTHENTICATED_USER_ID);
	}

	private static MessageEvent toMessageEvent(Message message) {
		String createdAt = message.getCreatedAt()
				.atZone(ZoneId.systemDefault())
				.format(ISO_DATE_TIME_FORMATTER);
		return new MessageEvent(MESSAGE_TYPE, message.getMessageId(), message.getDialogId(), message.getSenderId(),
				message.getContent(), createdAt);
	}

	private record MessageEvent(String type, UUID messageId, UUID dialogId, UUID senderId, String content,
			String createdAt) {
	}

	private record ErrorEvent(String type, String code, String message) {
	}

}
