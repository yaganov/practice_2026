package ru.ulstu.soapmessenger.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import ru.ulstu.soapmessenger.soap.JwtWebSocketHandshakeInterceptor;
import ru.ulstu.soapmessenger.soap.MessageWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

	private final MessageWebSocketHandler messageWebSocketHandler;
	private final JwtWebSocketHandshakeInterceptor jwtWebSocketHandshakeInterceptor;

	public WebSocketConfig(MessageWebSocketHandler messageWebSocketHandler,
			JwtWebSocketHandshakeInterceptor jwtWebSocketHandshakeInterceptor) {
		this.messageWebSocketHandler = messageWebSocketHandler;
		this.jwtWebSocketHandshakeInterceptor = jwtWebSocketHandshakeInterceptor;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(messageWebSocketHandler, "/websocket")
				.addInterceptors(jwtWebSocketHandshakeInterceptor);
	}

}
