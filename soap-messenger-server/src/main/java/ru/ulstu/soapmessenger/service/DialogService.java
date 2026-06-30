package ru.ulstu.soapmessenger.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.GregorianCalendar;
import java.util.UUID;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.ulstu.soapmessenger.exception.ServiceException;
import ru.ulstu.soapmessenger.model.Dialog;
import ru.ulstu.soapmessenger.model.User;
import ru.ulstu.soapmessenger.repository.DialogRepository;
import ru.ulstu.soapmessenger.repository.UserRepository;
import ru.ulstu.soapmessenger.soap.generated.DialogSummaryType;
import ru.ulstu.soapmessenger.soap.generated.ServiceErrorCodeType;
import ru.ulstu.soapmessenger.soap.generated.UserType;

@Service
public class DialogService {

	private static final String USER_NOT_FOUND_MESSAGE = "Пользователь не найден";
	private static final String SELF_DIALOG_MESSAGE = "Нельзя создать диалог с самим собой";

	private final DialogRepository dialogRepository;
	private final UserRepository userRepository;

	public DialogService(DialogRepository dialogRepository, UserRepository userRepository) {
		this.dialogRepository = dialogRepository;
		this.userRepository = userRepository;
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
