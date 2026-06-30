package ru.ulstu.soapmessenger.soap;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapFaultDetail;
import org.springframework.ws.soap.server.endpoint.AbstractSoapFaultDefinitionExceptionResolver;
import org.springframework.ws.soap.server.endpoint.SoapFaultDefinition;

import jakarta.xml.bind.JAXBElement;
import ru.ulstu.soapmessenger.endpoint.RegisterUserEndpoint;
import ru.ulstu.soapmessenger.exception.RegistrationValidationException;
import ru.ulstu.soapmessenger.exception.UsernameAlreadyExistsException;
import ru.ulstu.soapmessenger.soap.generated.ObjectFactory;
import ru.ulstu.soapmessenger.soap.generated.ServiceErrorCodeType;
import ru.ulstu.soapmessenger.soap.generated.ServiceFaultType;

@Component
public class RegisterUserFaultExceptionResolver extends AbstractSoapFaultDefinitionExceptionResolver {

	private static final FaultMapping USERNAME_ALREADY_EXISTS_MAPPING =
			new FaultMapping(SoapFaultDefinition.CLIENT, "Имя пользователя уже занято",
					ServiceErrorCodeType.USERNAME_ALREADY_EXISTS, "Имя пользователя уже занято");
	private static final FaultMapping INTERNAL_ERROR_MAPPING =
			new FaultMapping(SoapFaultDefinition.SERVER, "Внутренняя ошибка сервера",
					ServiceErrorCodeType.INTERNAL_ERROR, "Внутренняя ошибка сервера");

	private final Jaxb2Marshaller marshaller;
	private final ObjectFactory objectFactory;

	public RegisterUserFaultExceptionResolver(RegisterUserEndpoint registerUserEndpoint) {
		setMappedEndpoints(java.util.Set.of(registerUserEndpoint));
		marshaller = new Jaxb2Marshaller();
		marshaller.setContextPath("ru.ulstu.soapmessenger.soap.generated");
		objectFactory = new ObjectFactory();
		try {
			marshaller.afterPropertiesSet();
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to initialize JAXB marshaller for SOAP faults", ex);
		}
	}

	@Override
	protected SoapFaultDefinition getFaultDefinition(Object endpoint, Exception ex) {
		FaultMapping mapping = mapFault(resolveCause(ex));
		SoapFaultDefinition faultDefinition = new SoapFaultDefinition();
		faultDefinition.setFaultCode(mapping.faultCode());
		faultDefinition.setFaultStringOrReason(mapping.faultString());
		return faultDefinition;
	}

	@Override
	protected void customizeFault(Object endpoint, Exception ex, SoapFault fault) {
		FaultMapping mapping = mapFault(resolveCause(ex));
		ServiceFaultType faultDetail = new ServiceFaultType();
		faultDetail.setCode(mapping.detailCode());
		faultDetail.setMessage(mapping.detailMessage());
		try {
			JAXBElement<ServiceFaultType> faultElement = objectFactory.createRegisterUserFault(faultDetail);
			SoapFaultDetail soapFaultDetail = fault.addFaultDetail();
			marshaller.marshal(faultElement, soapFaultDetail.getResult());
		}
		catch (Exception marshalEx) {
			throw new IllegalStateException("Failed to marshal SOAP fault detail", marshalEx);
		}
	}

	private FaultMapping mapFault(Throwable cause) {
		if (cause instanceof RegistrationValidationException validationException) {
			return new FaultMapping(SoapFaultDefinition.CLIENT, validationException.getMessage(),
					ServiceErrorCodeType.VALIDATION_ERROR, validationException.getMessage());
		}
		if (cause instanceof UsernameAlreadyExistsException) {
			return USERNAME_ALREADY_EXISTS_MAPPING;
		}
		return INTERNAL_ERROR_MAPPING;
	}

	private Throwable resolveCause(Throwable ex) {
		Throwable current = ex;
		while (current != null) {
			if (current instanceof RegistrationValidationException
					|| current instanceof UsernameAlreadyExistsException
					|| current instanceof DataIntegrityViolationException) {
				return current;
			}
			current = current.getCause();
		}
		return ex;
	}

	private record FaultMapping(
			javax.xml.namespace.QName faultCode,
			String faultString,
			ServiceErrorCodeType detailCode,
			String detailMessage) {
	}

}
