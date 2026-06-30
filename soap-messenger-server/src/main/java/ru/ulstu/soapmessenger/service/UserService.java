package ru.ulstu.soapmessenger.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.ulstu.soapmessenger.exception.ServiceException;
import ru.ulstu.soapmessenger.model.User;
import ru.ulstu.soapmessenger.repository.UserRepository;
import ru.ulstu.soapmessenger.soap.generated.ServiceErrorCodeType;
import ru.ulstu.soapmessenger.soap.generated.UserType;

@Service
public class UserService {

	private static final int MIN_USERNAME_LENGTH = 5;
	private static final int MAX_USERNAME_LENGTH = 32;
	private static final int MIN_PASSWORD_LENGTH = 8;
	private static final int MAX_PASSWORD_LENGTH = 32;

	private static final String ISSUER = "soap-messenger";
	private static final String USERNAME_BLANK_MESSAGE = "Имя пользователя не может быть пустым";
	private static final String PASSWORD_BLANK_MESSAGE = "Пароль не может быть пустым";
	private static final String USERNAME_ALREADY_EXISTS_MESSAGE = "Имя пользователя уже занято";
	private static final String INVALID_CREDENTIALS_MESSAGE = "Неверное имя пользователя или пароль";
	private static final String USER_NOT_FOUND_MESSAGE = "Пользователь не найден";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtEncoder jwtEncoder;
	private final Duration tokenTtl;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
			@Value("${JWT_TTL:PT24H}") Duration tokenTtl) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtEncoder = jwtEncoder;
		this.tokenTtl = tokenTtl;
	}

	@Transactional
	public UUID registerUser(String username, String password) {
		validateUsername(username);
		validatePassword(password);

		if (userRepository.existsByUsername(username)) {
			throw new ServiceException(ServiceErrorCodeType.USERNAME_ALREADY_EXISTS, USERNAME_ALREADY_EXISTS_MESSAGE);
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
			throw new ServiceException(ServiceErrorCodeType.USERNAME_ALREADY_EXISTS, USERNAME_ALREADY_EXISTS_MESSAGE);
		}
	}

	public String authenticateUser(String username, String password) {
		validateUsername(username);
		validatePassword(password);

		User user = userRepository.findByUsername(username)
				.filter(foundUser -> passwordEncoder.matches(password, foundUser.getPasswordHash()))
				.orElseThrow(() -> new ServiceException(ServiceErrorCodeType.INVALID_CREDENTIALS,
						INVALID_CREDENTIALS_MESSAGE));
		return createJwtToken(user.getUserId());
	}

	public UserType findUser(String username) {
		validateUsername(username);
		return userRepository.findByUsername(username)
				.map(this::toUserType)
				.orElseThrow(() -> new ServiceException(ServiceErrorCodeType.USER_NOT_FOUND, USER_NOT_FOUND_MESSAGE));
	}

	public static String usernameTooShortMessage() {
		return "Имя пользователя должно содержать не менее " + MIN_USERNAME_LENGTH + " символов";
	}

	public static String usernameTooLongMessage() {
		return "Имя пользователя не должно быть длиннее " + MAX_USERNAME_LENGTH + " символов";
	}

	public static String passwordTooShortMessage() {
		return "Пароль должен содержать не менее " + MIN_PASSWORD_LENGTH + " символов";
	}

	public static String passwordTooLongMessage() {
		return "Пароль не должен быть длиннее " + MAX_PASSWORD_LENGTH + " символов";
	}

	private void validateUsername(String username) {
		validateNotBlank(username, USERNAME_BLANK_MESSAGE);
		int length = codePointLength(username);
		if (length < MIN_USERNAME_LENGTH) {
			throw new ServiceException(ServiceErrorCodeType.VALIDATION_ERROR, usernameTooShortMessage());
		}
		if (length > MAX_USERNAME_LENGTH) {
			throw new ServiceException(ServiceErrorCodeType.VALIDATION_ERROR, usernameTooLongMessage());
		}
	}

	private void validatePassword(String password) {
		validateNotBlank(password, PASSWORD_BLANK_MESSAGE);
		int length = codePointLength(password);
		if (length < MIN_PASSWORD_LENGTH) {
			throw new ServiceException(ServiceErrorCodeType.VALIDATION_ERROR, passwordTooShortMessage());
		}
		if (length > MAX_PASSWORD_LENGTH) {
			throw new ServiceException(ServiceErrorCodeType.VALIDATION_ERROR, passwordTooLongMessage());
		}
	}

	private void validateNotBlank(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new ServiceException(ServiceErrorCodeType.VALIDATION_ERROR, message);
		}
	}

	private String createJwtToken(UUID userId) {
		Instant issuedAt = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(ISSUER)
				.subject(userId.toString())
				.issuedAt(issuedAt)
				.expiresAt(issuedAt.plus(tokenTtl))
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private UserType toUserType(User user) {
		UserType userType = new UserType();
		userType.setUserId(user.getUserId().toString());
		userType.setUsername(user.getUsername());
		return userType;
	}

	private static int codePointLength(String value) {
		return value.codePointCount(0, value.length());
	}

}
