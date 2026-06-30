package ru.ulstu.soapmessenger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ru.ulstu.soapmessenger.exception.ServiceException;
import ru.ulstu.soapmessenger.model.Dialog;
import ru.ulstu.soapmessenger.model.Message;
import ru.ulstu.soapmessenger.model.User;
import ru.ulstu.soapmessenger.repository.DialogRepository;
import ru.ulstu.soapmessenger.repository.MessageRepository;
import ru.ulstu.soapmessenger.repository.UserRepository;
import ru.ulstu.soapmessenger.soap.generated.DialogSummaryType;
import ru.ulstu.soapmessenger.soap.generated.MessageType;
import ru.ulstu.soapmessenger.soap.generated.ServiceErrorCodeType;

@ExtendWith(MockitoExtension.class)
class DialogServiceTest {

	private static final String OTHER_USERNAME = "bob";

	@Mock
	private DialogRepository dialogRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private MessageRepository messageRepository;

	private DialogService dialogService;

	@BeforeEach
	void setUp() {
		dialogService = new DialogService(dialogRepository, userRepository, messageRepository);
	}

	@Test
	void openOrCreateDialog_createsDialogWithTwoParticipants() {
		UUID currentUserId = UUID.randomUUID();
		UUID otherUserId = UUID.randomUUID();
		User otherUser = createUser(otherUserId, OTHER_USERNAME);

		when(userRepository.findById(otherUserId)).thenReturn(Optional.of(otherUser));
		when(dialogRepository.findPersonalDialogIdBetweenUsers(currentUserId, otherUserId))
				.thenReturn(Optional.empty());
		when(dialogRepository.saveAndFlush(any(Dialog.class))).thenAnswer(invocation -> invocation.getArgument(0));

		DialogSummaryType result = dialogService.openOrCreateDialog(currentUserId, otherUserId);

		assertNotNull(result.getDialogId());
		assertEquals(otherUserId.toString(), result.getInterlocutor().getUserId());
		assertEquals(OTHER_USERNAME, result.getInterlocutor().getUsername());
		assertNotNull(result.getCreatedAt());

		ArgumentCaptor<Dialog> dialogCaptor = ArgumentCaptor.forClass(Dialog.class);
		verify(dialogRepository).saveAndFlush(dialogCaptor.capture());
		UUID dialogId = dialogCaptor.getValue().getDialogId();
		verify(dialogRepository).addParticipant(dialogId, currentUserId);
		verify(dialogRepository).addParticipant(dialogId, otherUserId);
	}

	@Test
	void openOrCreateDialog_returnsExistingDialog() {
		UUID currentUserId = UUID.randomUUID();
		UUID otherUserId = UUID.randomUUID();
		UUID dialogId = UUID.randomUUID();
		User otherUser = createUser(otherUserId, OTHER_USERNAME);
		Dialog existingDialog = new Dialog();
		existingDialog.setDialogId(dialogId);
		existingDialog.setCreatedAt(LocalDateTime.of(2026, 1, 15, 10, 30));

		when(userRepository.findById(otherUserId)).thenReturn(Optional.of(otherUser));
		when(dialogRepository.findPersonalDialogIdBetweenUsers(currentUserId, otherUserId))
				.thenReturn(Optional.of(dialogId));
		when(dialogRepository.findById(dialogId)).thenReturn(Optional.of(existingDialog));

		DialogSummaryType result = dialogService.openOrCreateDialog(currentUserId, otherUserId);

		assertEquals(dialogId.toString(), result.getDialogId());
		assertEquals(otherUserId.toString(), result.getInterlocutor().getUserId());
		verify(dialogRepository, never()).saveAndFlush(any(Dialog.class));
		verify(dialogRepository, never()).addParticipant(any(), any());
	}

	@Test
	void openOrCreateDialog_otherUserNotFound() {
		UUID currentUserId = UUID.randomUUID();
		UUID otherUserId = UUID.randomUUID();

		when(userRepository.findById(otherUserId)).thenReturn(Optional.empty());

		ServiceException ex = assertThrows(ServiceException.class,
				() -> dialogService.openOrCreateDialog(currentUserId, otherUserId));

		assertEquals(ServiceErrorCodeType.USER_NOT_FOUND, ex.getCode());
		assertEquals("Пользователь не найден", ex.getMessage());
		verify(dialogRepository, never()).saveAndFlush(any(Dialog.class));
	}

	@Test
	void openOrCreateDialog_selfDialogValidationError() {
		UUID userId = UUID.randomUUID();

		ServiceException ex = assertThrows(ServiceException.class,
				() -> dialogService.openOrCreateDialog(userId, userId));

		assertEquals(ServiceErrorCodeType.VALIDATION_ERROR, ex.getCode());
		assertEquals("Нельзя создать диалог с самим собой", ex.getMessage());
		verify(userRepository, never()).findById(any());
		verify(dialogRepository, never()).findPersonalDialogIdBetweenUsers(any(), any());
	}

	@Test
	void getDialogs_returnsDialogsWithInterlocutor() {
		UUID currentUserId = UUID.randomUUID();
		UUID dialogId = UUID.randomUUID();
		UUID otherUserId = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.of(2026, 3, 10, 14, 30);

		when(dialogRepository.findPersonalDialogsWithInterlocutor(currentUserId))
				.thenReturn(List.<Object[]>of(new Object[] {dialogId, createdAt, otherUserId, OTHER_USERNAME}));

		List<DialogSummaryType> result = dialogService.getDialogs(currentUserId);

		assertEquals(1, result.size());
		assertEquals(dialogId.toString(), result.get(0).getDialogId());
		assertEquals(otherUserId.toString(), result.get(0).getInterlocutor().getUserId());
		assertEquals(OTHER_USERNAME, result.get(0).getInterlocutor().getUsername());
		assertNotNull(result.get(0).getCreatedAt());
	}

	@Test
	void getDialogs_returnsEmptyList() {
		UUID currentUserId = UUID.randomUUID();

		when(dialogRepository.findPersonalDialogsWithInterlocutor(currentUserId)).thenReturn(List.of());

		List<DialogSummaryType> result = dialogService.getDialogs(currentUserId);

		assertTrue(result.isEmpty());
	}

	@Test
	void getMessageHistory_returnsMessagesInChronologicalOrder() {
		UUID currentUserId = UUID.randomUUID();
		UUID dialogId = UUID.randomUUID();
		UUID firstMessageId = UUID.randomUUID();
		UUID secondMessageId = UUID.randomUUID();
		LocalDateTime firstCreatedAt = LocalDateTime.of(2026, 3, 10, 10, 0);
		LocalDateTime secondCreatedAt = LocalDateTime.of(2026, 3, 10, 11, 0);

		Message firstMessage = createMessage(firstMessageId, dialogId, currentUserId, "hello", firstCreatedAt);
		Message secondMessage = createMessage(secondMessageId, dialogId, UUID.randomUUID(), "world", secondCreatedAt);

		when(dialogRepository.existsById(dialogId)).thenReturn(true);
		when(dialogRepository.isParticipant(dialogId, currentUserId)).thenReturn(true);
		when(messageRepository.findByDialogIdOrderByCreatedAtAsc(dialogId))
				.thenReturn(List.of(firstMessage, secondMessage));

		List<MessageType> result = dialogService.getMessageHistory(currentUserId, dialogId);

		assertEquals(2, result.size());
		assertEquals(firstMessageId.toString(), result.get(0).getMessageId());
		assertEquals("hello", result.get(0).getContent());
		assertEquals(secondMessageId.toString(), result.get(1).getMessageId());
		assertEquals("world", result.get(1).getContent());
	}

	@Test
	void getMessageHistory_accessDenied() {
		UUID currentUserId = UUID.randomUUID();
		UUID dialogId = UUID.randomUUID();

		when(dialogRepository.existsById(dialogId)).thenReturn(true);
		when(dialogRepository.isParticipant(dialogId, currentUserId)).thenReturn(false);

		ServiceException ex = assertThrows(ServiceException.class,
				() -> dialogService.getMessageHistory(currentUserId, dialogId));

		assertEquals(ServiceErrorCodeType.ACCESS_DENIED, ex.getCode());
		assertEquals("Нет доступа к диалогу", ex.getMessage());
		verify(messageRepository, never()).findByDialogIdOrderByCreatedAtAsc(any());
	}

	@Test
	void getMessageHistory_dialogNotFound() {
		UUID currentUserId = UUID.randomUUID();
		UUID dialogId = UUID.randomUUID();

		when(dialogRepository.existsById(dialogId)).thenReturn(false);

		ServiceException ex = assertThrows(ServiceException.class,
				() -> dialogService.getMessageHistory(currentUserId, dialogId));

		assertEquals(ServiceErrorCodeType.DIALOG_NOT_FOUND, ex.getCode());
		assertEquals("Диалог не найден", ex.getMessage());
		verify(dialogRepository, never()).isParticipant(any(), any());
		verify(messageRepository, never()).findByDialogIdOrderByCreatedAtAsc(any());
	}

	@Test
	void getMessageHistory_returnsEmptyList() {
		UUID currentUserId = UUID.randomUUID();
		UUID dialogId = UUID.randomUUID();

		when(dialogRepository.existsById(dialogId)).thenReturn(true);
		when(dialogRepository.isParticipant(dialogId, currentUserId)).thenReturn(true);
		when(messageRepository.findByDialogIdOrderByCreatedAtAsc(dialogId)).thenReturn(List.of());

		List<MessageType> result = dialogService.getMessageHistory(currentUserId, dialogId);

		assertTrue(result.isEmpty());
	}

	private static User createUser(UUID userId, String username) {
		User user = new User();
		user.setUserId(userId);
		user.setUsername(username);
		return user;
	}

	private static Message createMessage(UUID messageId, UUID dialogId, UUID senderId, String content,
			LocalDateTime createdAt) {
		Message message = new Message();
		message.setMessageId(messageId);
		message.setDialogId(dialogId);
		message.setSenderId(senderId);
		message.setClientMessageId(UUID.randomUUID());
		message.setContent(content);
		message.setCreatedAt(createdAt);
		return message;
	}

}
