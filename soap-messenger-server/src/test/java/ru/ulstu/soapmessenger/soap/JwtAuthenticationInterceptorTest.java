package ru.ulstu.soapmessenger.soap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.transport.context.DefaultTransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import ru.ulstu.soapmessenger.config.SecurityConfig;
import ru.ulstu.soapmessenger.exception.ServiceException;
import ru.ulstu.soapmessenger.soap.generated.ServiceErrorCodeType;

class JwtAuthenticationInterceptorTest {

	private static final String TEST_SECRET_BASE64 = "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYQ==";

	private JwtEncoder jwtEncoder;
	private JwtDecoder jwtDecoder;
	private JwtAuthenticationInterceptor interceptor;
	private MessageContext messageContext;

	@BeforeEach
	void setUp() {
		byte[] secretBytes = Base64.getDecoder().decode(TEST_SECRET_BASE64);
		SecretKey secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
		jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(
				new JWKSet(new OctetSequenceKey.Builder(secretBytes).algorithm(JWSAlgorithm.HS256).build())));
		NimbusJwtDecoder nimbusJwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
		nimbusJwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(SecurityConfig.JWT_ISSUER));
		jwtDecoder = nimbusJwtDecoder;
		interceptor = new JwtAuthenticationInterceptor(jwtDecoder);
		messageContext = mock(MessageContext.class);
	}

	@AfterEach
	void tearDown() {
		TransportContextHolder.setTransportContext(null);
	}

	@Test
	void validBearerToken_passes() throws Exception {
		String token = createToken(UUID.randomUUID(), Instant.now().plus(Duration.ofHours(1)));
		setAuthorizationHeader("Bearer " + token);

		assertTrue(interceptor.handleRequest(messageContext, new Object()));
	}

	@Test
	void missingAuthorizationHeader_throwsUnauthorized() throws Exception {
		setAuthorizationHeader(null);

		ServiceException ex = assertThrows(ServiceException.class,
				() -> interceptor.handleRequest(messageContext, new Object()));

		assertEquals(ServiceErrorCodeType.UNAUTHORIZED, ex.getCode());
		assertEquals("Требуется действующий токен авторизации", ex.getMessage());
	}

	@Test
	void malformedAuthorizationHeader_throwsUnauthorized() throws Exception {
		setAuthorizationHeader("Token abc");

		ServiceException ex = assertThrows(ServiceException.class,
				() -> interceptor.handleRequest(messageContext, new Object()));

		assertEquals(ServiceErrorCodeType.UNAUTHORIZED, ex.getCode());
	}

	@Test
	void corruptedJwt_throwsUnauthorized() throws Exception {
		setAuthorizationHeader("Bearer corrupted.jwt.token");

		ServiceException ex = assertThrows(ServiceException.class,
				() -> interceptor.handleRequest(messageContext, new Object()));

		assertEquals(ServiceErrorCodeType.UNAUTHORIZED, ex.getCode());
	}

	@Test
	void expiredJwt_throwsUnauthorized() throws Exception {
		String token = createToken(UUID.randomUUID(), Instant.now().minus(Duration.ofMinutes(5)));
		setAuthorizationHeader("Bearer " + token);

		ServiceException ex = assertThrows(ServiceException.class,
				() -> interceptor.handleRequest(messageContext, new Object()));

		assertEquals(ServiceErrorCodeType.UNAUTHORIZED, ex.getCode());
	}

	private String createToken(UUID userId, Instant expiresAt) {
		Instant issuedAt = expiresAt.minus(Duration.ofHours(1));
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(SecurityConfig.JWT_ISSUER)
				.subject(userId.toString())
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private static void setAuthorizationHeader(String authorizationHeader) throws Exception {
		HttpServletConnection connection = mock(HttpServletConnection.class);
		if (authorizationHeader == null) {
			when(connection.getRequestHeaders("Authorization")).thenReturn(Collections.emptyIterator());
		}
		else {
			when(connection.getRequestHeaders("Authorization"))
					.thenReturn(List.of(authorizationHeader).iterator());
		}
		TransportContextHolder.setTransportContext(new DefaultTransportContext(connection));
	}

}
