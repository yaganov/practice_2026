package ru.ulstu.soapmessenger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ru.ulstu.soapmessenger.repository.UserRepository;

@SpringBootTest
class SoapMessengerServerApplicationTests {

	@MockitoBean
	private UserRepository userRepository;

	@Test
	void contextLoads() {
	}

}
