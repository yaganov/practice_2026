package ru.ulstu.soapmessenger.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.ulstu.soapmessenger.exception.RegistrationValidationException;
import ru.ulstu.soapmessenger.exception.UsernameAlreadyExistsException;
import ru.ulstu.soapmessenger.model.User;
import ru.ulstu.soapmessenger.repository.UserRepository;

@Service
public class UserService {

	private static final int MIN_USERNAME_LENGTH = 5;
	private static final int MAX_USERNAME_LENGTH = 32;
	private static final int MIN_PASSWORD_LENGTH = 8;
	private static final int MAX_PASSWORD_LENGTH = 32;

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public UUID register(String username, String password) {
		validateUsername(username);
		validatePassword(password);

		if (userRepository.existsByUsername(username)) {
			throw new UsernameAlreadyExistsException();
		}

		User user = new User();
		user.setUserId(UUID.randomUUID());
		user.setUsername(username);
		user.setPasswordHash(passwordEncoder.encode(password));
		user.setCreatedAt(LocalDateTime.now());

		try {
			return userRepository.saveAndFlush(user).getUserId();
		}
		catch (DataIntegrityViolationException ex) {
			throw new UsernameAlreadyExistsException();
		}
	}

	private void validateUsername(String username) {
		validateNotBlank(username, "Имя пользователя не может быть пустым");
		int length = codePointLength(username);
		if (length < MIN_USERNAME_LENGTH) {
			throw new RegistrationValidationException("Имя пользователя должно содержать не менее 5 символов");
		}
		if (length > MAX_USERNAME_LENGTH) {
			throw new RegistrationValidationException("Имя пользователя не должно быть длиннее 32 символов");
		}
	}

	private void validatePassword(String password) {
		validateNotBlank(password, "Пароль не может быть пустым");
		int length = codePointLength(password);
		if (length < MIN_PASSWORD_LENGTH) {
			throw new RegistrationValidationException("Пароль должен содержать не менее 8 символов");
		}
		if (length > MAX_PASSWORD_LENGTH) {
			throw new RegistrationValidationException("Пароль не должен быть длиннее 32 символов");
		}
	}

	private void validateNotBlank(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new RegistrationValidationException(message);
		}
	}

	private static int codePointLength(String value) {
		return value.codePointCount(0, value.length());
	}

}
