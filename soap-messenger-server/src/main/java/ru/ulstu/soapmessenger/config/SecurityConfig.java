package ru.ulstu.soapmessenger.config;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

@Configuration
public class SecurityConfig {

	private static final int MIN_SECRET_BYTES = 32;
	public static final String JWT_ISSUER = "soap-messenger";

	@Bean
	public PasswordEncoder passwordEncoder() {
		return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
	}

	@Bean
	public SecretKey jwtSecretKey(Environment environment) {
		return new SecretKeySpec(decodeAndValidateSecret(environment.getProperty("JWT_SECRET")), "HmacSHA256");
	}

	@Bean
	public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
		OctetSequenceKey jwk = new OctetSequenceKey.Builder(jwtSecretKey.getEncoded())
				.algorithm(JWSAlgorithm.HS256)
				.build();
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
	}

	@Bean
	public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey).build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JWT_ISSUER));
		return decoder;
	}

	private static byte[] decodeAndValidateSecret(String jwtSecretBase64) {
		if (jwtSecretBase64 == null || jwtSecretBase64.isBlank()) {
			throw new IllegalStateException("JWT_SECRET environment variable is required");
		}
		byte[] secretBytes;
		try {
			secretBytes = Base64.getDecoder().decode(jwtSecretBase64.trim());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("JWT_SECRET must be a valid Base64 string", ex);
		}
		if (secretBytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes");
		}
		return secretBytes;
	}

}
