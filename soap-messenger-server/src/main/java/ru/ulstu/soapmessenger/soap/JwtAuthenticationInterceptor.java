package ru.ulstu.soapmessenger.soap;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.transport.context.TransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;

import ru.ulstu.soapmessenger.exception.ServiceException;
import ru.ulstu.soapmessenger.soap.generated.ServiceErrorCodeType;

public class JwtAuthenticationInterceptor implements EndpointInterceptor {

	public static final String AUTHENTICATED_USER_ID = "authenticatedUserId";

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";
	private static final String UNAUTHORIZED_MESSAGE = "Требуется действующий токен авторизации";

	private final JwtDecoder jwtDecoder;

	public JwtAuthenticationInterceptor(JwtDecoder jwtDecoder) {
		this.jwtDecoder = jwtDecoder;
	}

	@Override
	public boolean handleRequest(MessageContext messageContext, Object endpoint) {
		String authorizationHeader = resolveAuthorizationHeader();
		if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
			throw unauthorized();
		}
		String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
		if (token.isEmpty()) {
			throw unauthorized();
		}
		try {
			Jwt jwt = jwtDecoder.decode(token);
			UUID userId = UUID.fromString(jwt.getSubject());
			messageContext.setProperty(AUTHENTICATED_USER_ID, userId);
		}
		catch (JwtException | IllegalArgumentException ex) {
			throw unauthorized();
		}
		return true;
	}

	@Override
	public boolean handleResponse(MessageContext messageContext, Object endpoint) {
		return true;
	}

	@Override
	public boolean handleFault(MessageContext messageContext, Object endpoint) {
		return true;
	}

	@Override
	public void afterCompletion(MessageContext messageContext, Object endpoint, Exception ex) {
		// no-op
	}

	private String resolveAuthorizationHeader() {
		TransportContext transportContext = TransportContextHolder.getTransportContext();
		if (transportContext == null) {
			return null;
		}
		if (!(transportContext.getConnection() instanceof HttpServletConnection connection)) {
			return null;
		}
		try {
			var headers = connection.getRequestHeaders(AUTHORIZATION_HEADER);
			if (headers == null || !headers.hasNext()) {
				return null;
			}
			return headers.next();
		}
		catch (java.io.IOException ex) {
			return null;
		}
	}

	private static ServiceException unauthorized() {
		return new ServiceException(ServiceErrorCodeType.UNAUTHORIZED, UNAUTHORIZED_MESSAGE);
	}

}
