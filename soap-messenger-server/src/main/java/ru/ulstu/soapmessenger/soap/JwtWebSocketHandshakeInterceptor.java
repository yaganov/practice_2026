package ru.ulstu.soapmessenger.soap;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class JwtWebSocketHandshakeInterceptor implements HandshakeInterceptor {

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtDecoder jwtDecoder;

	public JwtWebSocketHandshakeInterceptor(JwtDecoder jwtDecoder) {
		this.jwtDecoder = jwtDecoder;
	}

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Map<String, Object> attributes) {
		String authorizationHeader = resolveAuthorizationHeader(request);
		if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
			return false;
		}
		String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
		if (token.isEmpty()) {
			return false;
		}
		try {
			Jwt jwt = jwtDecoder.decode(token);
			attributes.put(JwtAuthenticationInterceptor.AUTHENTICATED_USER_ID, UUID.fromString(jwt.getSubject()));
			return true;
		}
		catch (JwtException | IllegalArgumentException ex) {
			return false;
		}
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Exception exception) {
		// no-op
	}

	private static String resolveAuthorizationHeader(ServerHttpRequest request) {
		List<String> headers = request.getHeaders().get(AUTHORIZATION_HEADER);
		if (headers == null || headers.isEmpty()) {
			return null;
		}
		return headers.getFirst();
	}

}
