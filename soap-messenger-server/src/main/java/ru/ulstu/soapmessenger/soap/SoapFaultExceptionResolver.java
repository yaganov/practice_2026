package ru.ulstu.soapmessenger.soap;

import java.util.Set;

import javax.xml.namespace.QName;

import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapFaultDetail;
import org.springframework.ws.soap.server.endpoint.AbstractSoapFaultDefinitionExceptionResolver;
import org.springframework.ws.soap.server.endpoint.SoapFaultDefinition;

import jakarta.xml.bind.JAXBElement;
import ru.ulstu.soapmessenger.exception.ServiceException;
import ru.ulstu.soapmessenger.soap.generated.ObjectFactory;
import ru.ulstu.soapmessenger.soap.generated.ServiceErrorCodeType;
import ru.ulstu.soapmessenger.soap.generated.ServiceFaultType;

public class SoapFaultExceptionResolver extends AbstractSoapFaultDefinitionExceptionResolver {

	private static final String INTERNAL_ERROR_MESSAGE = "Внутренняя ошибка сервера";
	private static final FaultMapping INTERNAL_ERROR_MAPPING =
			new FaultMapping(SoapFaultDefinition.SERVER, INTERNAL_ERROR_MESSAGE,
					ServiceErrorCodeType.INTERNAL_ERROR, INTERNAL_ERROR_MESSAGE);

	private final ObjectFactory objectFactory = new ObjectFactory();
	private final Jaxb2Marshaller marshaller;
	private final boolean authenticateFault;

	public SoapFaultExceptionResolver(Object endpoint, boolean authenticateFault) {
		if (endpoint != null) {
			setMappedEndpoints(Set.of(endpoint));
		}
		this.authenticateFault = authenticateFault;
		marshaller = new Jaxb2Marshaller();
		marshaller.setContextPath("ru.ulstu.soapmessenger.soap.generated");
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
		try {
			FaultMapping mapping = mapFault(resolveCause(ex));
			ServiceFaultType faultDetail = new ServiceFaultType();
			faultDetail.setCode(mapping.detailCode());
			faultDetail.setMessage(mapping.detailMessage());
			JAXBElement<ServiceFaultType> faultElement = createFaultElement(faultDetail);
			SoapFaultDetail soapFaultDetail = fault.addFaultDetail();
			marshaller.marshal(faultElement, soapFaultDetail.getResult());
		}
		catch (Exception marshalEx) {
			throw new IllegalStateException("Failed to marshal SOAP fault detail", marshalEx);
		}
	}

	private FaultMapping mapFault(Throwable cause) {
		if (cause instanceof ServiceException serviceException) {
			return new FaultMapping(SoapFaultDefinition.CLIENT, serviceException.getMessage(),
					serviceException.getCode(), serviceException.getMessage());
		}
		return INTERNAL_ERROR_MAPPING;
	}

	private Throwable resolveCause(Throwable ex) {
		Throwable current = ex;
		while (current != null) {
			if (current instanceof ServiceException) {
				return current;
			}
			current = current.getCause();
		}
		return ex;
	}

	private JAXBElement<ServiceFaultType> createFaultElement(ServiceFaultType faultDetail) {
		if (authenticateFault) {
			return objectFactory.createAuthenticateUserFault(faultDetail);
		}
		return objectFactory.createRegisterUserFault(faultDetail);
	}

	private record FaultMapping(
			QName faultCode,
			String faultString,
			ServiceErrorCodeType detailCode,
			String detailMessage) {
	}

}
