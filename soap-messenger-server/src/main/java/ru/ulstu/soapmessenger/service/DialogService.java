package ru.ulstu.soapmessenger.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.UUID;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import ru.ulstu.soapmessenger.soap.generated.UserType;

@Service
public class DialogService {

	private static final String USER_NOT_FOUND_MESSAGE = "Пользователь не найден";
	private static final String SELF_DIALOG_MESSAGE = "Нельзя создать диалог с самим собой";
	private static final String DIALOG_NOT_FOUND_MESSAGE = "Диалог не найден";
	private static final String ACCESS_DENIED_MESSAGE = "Нет доступа к диалогу";

	private final DialogRepository dialogRepository;
	private final UserRepository userRepository;
	private final MessageRepository messageRepository;

	public DialogService(DialogRepository dialogRepository, UserRepository userRepository,
			MessageRepository messageRepository) {
		this.dialogRepository = dialogRepository;
		this.userRepository = userRepository;
		this.messageRepository = messageRepository;
	}

	@Transactional
	public DialogSummaryType openOrCreateDialog(UUID currentUserId, UUID otherUserId) {
		if (currentUserId.equals(otherUserId)) {
			throw new ServiceException(ServiceErrorCodeType.VALIDATION_ERROR, SELF_DIALOG_MESSAGE);
		}

		User otherUser = userRepository.findById(otherUserId)
				.orElseThrow(() -> new ServiceException(ServiceErrorCodeType.USER_NOT_FOUND, USER_NOT_FOUND_MESSAGE));

		Dialog dialog = dialogRepository.findPersonalDialogIdBetweenUsers(currentUserId, otherUserId)
				.flatMap(dialogRepository::findById)
				.orElseGet(() -> createDialog(currentUserId, otherUserId));

		return toDialogSummary(dialog, otherUser);
	}

	@Transactional(readOnly = true)
	public List<DialogSummaryType> getDialogs(UUID currentUserId) {
		return dialogRepository.findPersonalDialogsWithInterlocutor(currentUserId).stream()
				.map(this::toDialogSummary)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<MessageType> getMessageHistory(UUID currentUserId, UUID dialogId) {
		if (!dialogRepository.existsById(dialogId)) {
			throw new ServiceException(ServiceErrorCodeType.DIALOG_NOT_FOUND, DIALOG_NOT_FOUND_MESSAGE);
		}
		if (!dialogRepository.isParticipant(dialogId, currentUserId)) {
			throw new ServiceException(ServiceErrorCodeType.ACCESS_DENIED, ACCESS_DENIED_MESSAGE);
		}
		return messageRepository.findByDialogIdOrderByCreatedAtAsc(dialogId).stream()
				.map(this::toMessageType)
				.toList();
	}

	private DialogSummaryType toDialogSummary(Object[] row) {
		Dialog dialog = new Dialog();
		dialog.setDialogId((UUID) row[0]);
		dialog.setCreatedAt((LocalDateTime) row[1]);

		User interlocutor = new User();
		interlocutor.setUserId((UUID) row[2]);
		interlocutor.setUsername((String) row[3]);

		return toDialogSummary(dialog, interlocutor);
	}

	private Dialog createDialog(UUID currentUserId, UUID otherUserId) {
		Dialog dialog = new Dialog();
		dialog.setDialogId(UUID.randomUUID());
		dialog.setCreatedAt(LocalDateTime.now());
		dialogRepository.saveAndFlush(dialog);
		dialogRepository.addParticipant(dialog.getDialogId(), currentUserId);
		dialogRepository.addParticipant(dialog.getDialogId(), otherUserId);
		return dialog;
	}

	private DialogSummaryType toDialogSummary(Dialog dialog, User interlocutor) {
		DialogSummaryType dialogSummary = new DialogSummaryType();
		dialogSummary.setDialogId(dialog.getDialogId().toString());

		UserType interlocutorType = new UserType();
		interlocutorType.setUserId(interlocutor.getUserId().toString());
		interlocutorType.setUsername(interlocutor.getUsername());
		dialogSummary.setInterlocutor(interlocutorType);
		dialogSummary.setCreatedAt(toXmlDateTime(dialog.getCreatedAt()));
		return dialogSummary;
	}

	private MessageType toMessageType(Message message) {
		MessageType messageType = new MessageType();
		messageType.setMessageId(message.getMessageId().toString());
		messageType.setSenderId(message.getSenderId().toString());
		messageType.setContent(message.getContent());
		messageType.setCreatedAt(toXmlDateTime(message.getCreatedAt()));
		return messageType;
	}

	private static XMLGregorianCalendar toXmlDateTime(LocalDateTime dateTime) {
		try {
			GregorianCalendar calendar = GregorianCalendar.from(dateTime.atZone(ZoneId.systemDefault()));
			return DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar);
		}
		catch (DatatypeConfigurationException ex) {
			throw new IllegalStateException("Failed to convert date time", ex);
		}
	}

}
