package ru.ulstu.soapmessenger.exception;

import ru.ulstu.soapmessenger.soap.generated.ServiceErrorCodeType;

public class ServiceException extends RuntimeException {

	private final ServiceErrorCodeType code;

	public ServiceException(ServiceErrorCodeType code, String message) {
		super(message);
		this.code = code;
	}

	public ServiceErrorCodeType getCode() {
		return code;
	}

}
