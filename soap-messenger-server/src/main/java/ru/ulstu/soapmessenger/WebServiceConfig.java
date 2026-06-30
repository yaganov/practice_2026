package ru.ulstu.soapmessenger;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.soap.server.endpoint.interceptor.PayloadRootSmartSoapEndpointInterceptor;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

import ru.ulstu.soapmessenger.endpoint.AuthenticateUserEndpoint;
import ru.ulstu.soapmessenger.endpoint.FindUserEndpoint;
import ru.ulstu.soapmessenger.endpoint.GetDialogsEndpoint;
import ru.ulstu.soapmessenger.endpoint.OpenOrCreateDialogEndpoint;
import ru.ulstu.soapmessenger.endpoint.RegisterUserEndpoint;
import ru.ulstu.soapmessenger.soap.JwtAuthenticationInterceptor;
import ru.ulstu.soapmessenger.soap.SoapFaultExceptionResolver;

@Configuration
@EnableWs
public class WebServiceConfig {

	private static final String NAMESPACE = "urn:soap-messenger:v1";

	@Bean
	public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext applicationContext) {
		MessageDispatcherServlet servlet = new MessageDispatcherServlet();
		servlet.setApplicationContext(applicationContext);
		servlet.setTransformWsdlLocations(true);
		return new ServletRegistrationBean<>(servlet, "/ws/*");
	}

	@Bean(name = "soap-messenger")
	public DefaultWsdl11Definition soapMessengerWsdl(XsdSchema soapMessengerSchema) {
		DefaultWsdl11Definition wsdl11Definition = new DefaultWsdl11Definition();
		wsdl11Definition.setPortTypeName("SoapMessengerPort");
		wsdl11Definition.setLocationUri("/ws");
		wsdl11Definition.setTargetNamespace(NAMESPACE);
		wsdl11Definition.setSchema(soapMessengerSchema);
		return wsdl11Definition;
	}

	@Bean
	public XsdSchema soapMessengerSchema() {
		return new SimpleXsdSchema(new ClassPathResource("META-INF/schemas/soap-messenger.xsd"));
	}

	@Bean
	public JwtAuthenticationInterceptor jwtAuthenticationInterceptor(JwtDecoder jwtDecoder) {
		return new JwtAuthenticationInterceptor(jwtDecoder);
	}

	@Bean
	public PayloadRootSmartSoapEndpointInterceptor findUserJwtAuthenticationInterceptor(
			JwtAuthenticationInterceptor jwtAuthenticationInterceptor) {
		return new PayloadRootSmartSoapEndpointInterceptor(jwtAuthenticationInterceptor, NAMESPACE, "FindUserRequest");
	}

	@Bean
	public PayloadRootSmartSoapEndpointInterceptor openOrCreateDialogJwtAuthenticationInterceptor(
			JwtAuthenticationInterceptor jwtAuthenticationInterceptor) {
		return new PayloadRootSmartSoapEndpointInterceptor(jwtAuthenticationInterceptor, NAMESPACE,
				"OpenOrCreateDialogRequest");
	}

	@Bean
	public PayloadRootSmartSoapEndpointInterceptor getDialogsJwtAuthenticationInterceptor(
			JwtAuthenticationInterceptor jwtAuthenticationInterceptor) {
		return new PayloadRootSmartSoapEndpointInterceptor(jwtAuthenticationInterceptor, NAMESPACE, "GetDialogsRequest");
	}

	@Bean
	public SoapFaultExceptionResolver registerUserFaultExceptionResolver(RegisterUserEndpoint registerUserEndpoint) {
		return new SoapFaultExceptionResolver(registerUserEndpoint, false);
	}

	@Bean
	public SoapFaultExceptionResolver authenticateUserFaultExceptionResolver(
			AuthenticateUserEndpoint authenticateUserEndpoint) {
		return new SoapFaultExceptionResolver(authenticateUserEndpoint, true);
	}

	@Bean
	public SoapFaultExceptionResolver findUserFaultExceptionResolver(FindUserEndpoint findUserEndpoint) {
		return new SoapFaultExceptionResolver(findUserEndpoint, SoapFaultExceptionResolver.FAULT_DETAIL_FIND_USER);
	}

	@Bean
	public SoapFaultExceptionResolver openOrCreateDialogFaultExceptionResolver(
			OpenOrCreateDialogEndpoint openOrCreateDialogEndpoint) {
		return new SoapFaultExceptionResolver(openOrCreateDialogEndpoint,
				SoapFaultExceptionResolver.FAULT_DETAIL_OPEN_OR_CREATE_DIALOG);
	}

	@Bean
	public SoapFaultExceptionResolver getDialogsFaultExceptionResolver(GetDialogsEndpoint getDialogsEndpoint) {
		return new SoapFaultExceptionResolver(getDialogsEndpoint, SoapFaultExceptionResolver.FAULT_DETAIL_GET_DIALOGS);
	}

}
