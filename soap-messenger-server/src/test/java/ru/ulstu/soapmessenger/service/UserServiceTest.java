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

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import ru.ulstu.soapmessenger.exception.ServiceException;
import ru.ulstu.soapmessenger.model.User;
import ru.ulstu.soapmessenger.repository.UserRepository;
import ru.ulstu.soapmessenger.soap.generated.ServiceErrorCodeType;
import ru.ulstu.soapmessenger.soap.generated.UserType;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	private static final String TEST_USERNAME = "test-user";
	private static final String TEST_PASSWORD = "test-password";
	private static final String TEST_SECRET_BASE64 = "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYQ==";

	@Mock
	private UserRepository userRepository;

	private PasswordEncoder passwordEncoder;
	private JwtDecoder jwtDecoder;
	private UserService userService;

	@BeforeEach
	void setUp() {
		passwordEncoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
		byte[] secretBytes = Base64.getDecoder().decode(TEST_SECRET_BASE64);
		SecretKey secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
		JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(
				new JWKSet(new OctetSequenceKey.Builder(secretBytes).algorithm(JWSAlgorithm.HS256).build())));
		jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
		userService = new UserService(userRepository, passwordEncoder, jwtEncoder, Duration.ofHours(24));
	}

	@Test
	void registerUser_success() {
		when(userRepository.existsByUsername(TEST_USERNAME)).thenReturn(false);
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UUID userId = userService.registerUser(TEST_USERNAME, TEST_PASSWORD);

		assertNotNull(userId);

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(userCaptor.capture());

		User savedUser = userCaptor.getValue();
		assertEquals(userId, savedUser.getUserId());
		assertEquals(TEST_USERNAME, savedUser.getUsername());
		assertNotEquals(TEST_PASSWORD, savedUser.getPasswordHash());
		assertTrue(passwordEncoder.matches(TEST_PASSWORD, savedUser.getPasswordHash()));
	}

	@ParameterizedTest
	@MethodSource("invalidCredentialsCases")
	void registerUser_invalidCredentials(String username, String password) {
		assertThrows(ServiceException.class, () -> userService.registerUser(username, password));

		verify(userRepository, never()).save(any(User.class));
		verify(userRepository, never()).saveAndFlush(any(User.class));
	}

	@Test
	void registerUser_usernameAlreadyExists() {
		when(userRepository.existsByUsername(TEST_USERNAME)).thenReturn(true);

		ServiceException ex = assertThrows(ServiceException.class,
				() -> userService.registerUser(TEST_USERNAME, TEST_PASSWORD));

		assertEquals(ServiceErrorCodeType.USERNAME_ALREADY_EXISTS, ex.getCode());
		verify(userRepository, never()).saveAndFlush(any(User.class));
	}

	@Test
	void authenticateUser_success() {
		UUID userId = UUID.randomUUID();
		User user = new User();
		user.setUserId(userId);
		user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));

		when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(java.util.Optional.of(user));

		String token = userService.authenticateUser(TEST_USERNAME, TEST_PASSWORD);

		Jwt jwt = jwtDecoder.decode(token);
		assertEquals(userId.toString(), jwt.getSubject());
		assertEquals("soap-messenger", jwt.getClaimAsString("iss"));
		assertNotNull(jwt.getIssuedAt());
		assertNotNull(jwt.getExpiresAt());
		assertTrue(jwt.getExpiresAt().isAfter(jwt.getIssuedAt()));
	}

	@Test
	void authenticateUser_userNotFound() {
		when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(java.util.Optional.empty());

		ServiceException ex = assertThrows(ServiceException.class,
				() -> userService.authenticateUser(TEST_USERNAME, TEST_PASSWORD));

		assertEquals(ServiceErrorCodeType.INVALID_CREDENTIALS, ex.getCode());
		assertEquals("Неверное имя пользователя или пароль", ex.getMessage());
	}

	@Test
	void authenticateUser_wrongPassword() {
		User user = new User();
		user.setPasswordHash(passwordEncoder.encode("other-password"));

		when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(java.util.Optional.of(user));

		ServiceException ex = assertThrows(ServiceException.class,
				() -> userService.authenticateUser(TEST_USERNAME, TEST_PASSWORD));

		assertEquals(ServiceErrorCodeType.INVALID_CREDENTIALS, ex.getCode());
		assertEquals("Неверное имя пользователя или пароль", ex.getMessage());
	}

	@Test
	void findUser_success() {
		UUID userId = UUID.randomUUID();
		User user = new User();
		user.setUserId(userId);
		user.setUsername(TEST_USERNAME);

		when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(java.util.Optional.of(user));

		UserType result = userService.findUser(TEST_USERNAME);

		assertEquals(userId.toString(), result.getUserId());
		assertEquals(TEST_USERNAME, result.getUsername());
	}

	@Test
	void findUser_userNotFound() {
		when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(java.util.Optional.empty());

		ServiceException ex = assertThrows(ServiceException.class, () -> userService.findUser(TEST_USERNAME));

		assertEquals(ServiceErrorCodeType.USER_NOT_FOUND, ex.getCode());
		assertEquals("Пользователь не найден", ex.getMessage());
	}

	private static Stream<Arguments> invalidCredentialsCases() {
		return Stream.of(
				Arguments.of("abcd", TEST_PASSWORD),
				Arguments.of("a".repeat(33), TEST_PASSWORD),
				Arguments.of("   ", TEST_PASSWORD),
				Arguments.of(TEST_USERNAME, "1234567"),
				Arguments.of(TEST_USERNAME, "p".repeat(33)),
				Arguments.of(TEST_USERNAME, "   "));
	}

}
