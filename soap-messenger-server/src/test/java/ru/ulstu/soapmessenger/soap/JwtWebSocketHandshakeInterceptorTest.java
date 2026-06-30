package ru.ulstu.soapmessenger.soap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.socket.WebSocketHandler;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import ru.ulstu.soapmessenger.config.SecurityConfig;

class JwtWebSocketHandshakeInterceptorTest {

	private static final String TEST_SECRET_BASE64 = "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYQ==";

	private JwtEncoder jwtEncoder;
	private JwtWebSocketHandshakeInterceptor interceptor;
	private ServerHttpRequest request;
	private ServerHttpResponse response;
	private WebSocketHandler webSocketHandler;

	@BeforeEach
	void setUp() {
		byte[] secretBytes = Base64.getDecoder().decode(TEST_SECRET_BASE64);
		SecretKey secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
		jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(
				new JWKSet(new OctetSequenceKey.Builder(secretBytes).algorithm(JWSAlgorithm.HS256).build())));
		NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
		jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(SecurityConfig.JWT_ISSUER));
		interceptor = new JwtWebSocketHandshakeInterceptor(jwtDecoder);
		request = mock(ServerHttpRequest.class);
		response = mock(ServerHttpResponse.class);
		webSocketHandler = mock(WebSocketHandler.class);
	}

	@Test
	void validBearerToken_allowsHandshakeAndStoresUserId() {
		UUID userId = UUID.randomUUID();
		String token = createToken(userId, Instant.now().plus(Duration.ofHours(1)));
		whenAuthorizationHeader("Bearer " + token);

		Map<String, Object> attributes = new HashMap<>();
		assertTrue(interceptor.beforeHandshake(request, response, webSocketHandler, attributes));
		assertEquals(userId, attributes.get(JwtAuthenticationInterceptor.AUTHENTICATED_USER_ID));
	}

	@Test
	void missingOrInvalidBearerToken_rejectsHandshake() {
		whenAuthorizationHeader(null);
		assertFalse(interceptor.beforeHandshake(request, response, webSocketHandler, new HashMap<>()));

		whenAuthorizationHeader("Bearer invalid.jwt.token");
		assertFalse(interceptor.beforeHandshake(request, response, webSocketHandler, new HashMap<>()));
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

	private void whenAuthorizationHeader(String authorizationHeader) {
		HttpHeaders headers = new HttpHeaders();
		if (authorizationHeader != null) {
			headers.set("Authorization", authorizationHeader);
		}
		org.mockito.Mockito.when(request.getHeaders()).thenReturn(headers);
	}

}
