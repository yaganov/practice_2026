package ru.ulstu.soapmessenger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

import ru.ulstu.soapmessenger.exception.RegistrationValidationException;
import ru.ulstu.soapmessenger.exception.UsernameAlreadyExistsException;
import ru.ulstu.soapmessenger.model.User;
import ru.ulstu.soapmessenger.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	private static final String TEST_USERNAME = "test-user";
	private static final String TEST_PASSWORD = "test-password";

	@Mock
	private UserRepository userRepository;

	private PasswordEncoder passwordEncoder;
	private UserService userService;

	@BeforeEach
	void setUp() {
		passwordEncoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
		userService = new UserService(userRepository, passwordEncoder);
	}

	@Test
	void register_success() {
		when(userRepository.existsByUsername(TEST_USERNAME)).thenReturn(false);
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UUID userId = userService.register(TEST_USERNAME, TEST_PASSWORD);

		assertNotNull(userId);

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(userCaptor.capture());

		User savedUser = userCaptor.getValue();
		assertEquals(userId, savedUser.getUserId());
		assertEquals(TEST_USERNAME, savedUser.getUsername());
		assertNotNull(savedUser.getCreatedAt());
		assertNotEquals(TEST_PASSWORD, savedUser.getPasswordHash());
		assertTrue(savedUser.getPasswordHash().length() <= 255);
		assertTrue(passwordEncoder.matches(TEST_PASSWORD, savedUser.getPasswordHash()));
	}

	@Test
	void register_usernameAlreadyExists() {
		when(userRepository.existsByUsername(TEST_USERNAME)).thenReturn(true);

		assertThrows(UsernameAlreadyExistsException.class,
				() -> userService.register(TEST_USERNAME, TEST_PASSWORD));

		verify(userRepository, never()).save(any(User.class));
		verify(userRepository, never()).saveAndFlush(any(User.class));
	}

	@Test
	void register_blankUsername() {
		assertThrows(RegistrationValidationException.class,
				() -> userService.register("   ", TEST_PASSWORD));

		verify(userRepository, never()).save(any(User.class));
		verify(userRepository, never()).saveAndFlush(any(User.class));
	}

	@Test
	void register_blankPassword() {
		assertThrows(RegistrationValidationException.class,
				() -> userService.register(TEST_USERNAME, "   "));

		verify(userRepository, never()).save(any(User.class));
		verify(userRepository, never()).saveAndFlush(any(User.class));
	}

	@Test
	void register_usernameTooShort() {
		assertThrows(RegistrationValidationException.class,
				() -> userService.register("abcd", TEST_PASSWORD));

		verify(userRepository, never()).save(any(User.class));
		verify(userRepository, never()).saveAndFlush(any(User.class));
	}

	@Test
	void register_usernameMinLength() {
		when(userRepository.existsByUsername("abcde")).thenReturn(false);
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UUID userId = userService.register("abcde", TEST_PASSWORD);

		assertNotNull(userId);
		verify(userRepository).saveAndFlush(any(User.class));
	}

	@Test
	void register_usernameMaxLength() {
		String username = "a".repeat(32);
		when(userRepository.existsByUsername(username)).thenReturn(false);
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UUID userId = userService.register(username, TEST_PASSWORD);

		assertNotNull(userId);
		verify(userRepository).saveAndFlush(any(User.class));
	}

	@Test
	void register_usernameTooLong() {
		assertThrows(RegistrationValidationException.class,
				() -> userService.register("a".repeat(33), TEST_PASSWORD));

		verify(userRepository, never()).save(any(User.class));
		verify(userRepository, never()).saveAndFlush(any(User.class));
	}

	@Test
	void register_passwordTooShort() {
		assertThrows(RegistrationValidationException.class,
				() -> userService.register(TEST_USERNAME, "1234567"));

		verify(userRepository, never()).save(any(User.class));
		verify(userRepository, never()).saveAndFlush(any(User.class));
	}

	@Test
	void register_passwordMinLength() {
		when(userRepository.existsByUsername(TEST_USERNAME)).thenReturn(false);
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		String password = "12345678";
		UUID userId = userService.register(TEST_USERNAME, password);

		assertNotNull(userId);

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(userCaptor.capture());
		assertNotEquals(password, userCaptor.getValue().getPasswordHash());
		assertTrue(passwordEncoder.matches(password, userCaptor.getValue().getPasswordHash()));
	}

	@Test
	void register_passwordMaxLength() {
		when(userRepository.existsByUsername(TEST_USERNAME)).thenReturn(false);
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		String password = "p".repeat(32);
		UUID userId = userService.register(TEST_USERNAME, password);

		assertNotNull(userId);

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(userCaptor.capture());
		assertNotEquals(password, userCaptor.getValue().getPasswordHash());
		assertTrue(passwordEncoder.matches(password, userCaptor.getValue().getPasswordHash()));
	}

	@Test
	void register_passwordTooLong() {
		assertThrows(RegistrationValidationException.class,
				() -> userService.register(TEST_USERNAME, "p".repeat(33)));

		verify(userRepository, never()).save(any(User.class));
		verify(userRepository, never()).saveAndFlush(any(User.class));
	}

	@Test
	void register_uniqueConstraintViolationDuringFlush() {
		when(userRepository.existsByUsername(TEST_USERNAME)).thenReturn(false);
		when(userRepository.saveAndFlush(any(User.class)))
				.thenThrow(new DataIntegrityViolationException("unique violation"));

		assertThrows(UsernameAlreadyExistsException.class,
				() -> userService.register(TEST_USERNAME, TEST_PASSWORD));
	}

}
