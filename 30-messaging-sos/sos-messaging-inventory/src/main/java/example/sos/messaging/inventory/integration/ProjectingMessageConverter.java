/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example.sos.messaging.inventory.integration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.core.ResolvableType;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.web.JsonProjectingMethodInterceptorFactory;
import org.springframework.kafka.support.KafkaNull;
import org.springframework.kafka.support.converter.ConversionException;
import org.springframework.kafka.support.converter.MessageConverter;
import org.springframework.kafka.support.converter.MessagingMessageConverter;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

/**
 * A {@link MessageConverter} implementation that uses a Spring Data {@link ProjectionFactory} to bind incoming messages
 * to projection interfaces.
 *
 * @author Oliver Gierke
 */
@Component
class ProjectingMessageConverter extends MessagingMessageConverter {

	private final ProjectionFactory projectionFactory;
	private final DelegatableStringJsonMessageConverter delegate;

	/**
	 * Creates a new {@link ProjectingMessageConverter} using the given {@link ObjectMapper}.
	 *
	 * @param mapper must not be {@literal null}.
	 */
	public ProjectingMessageConverter(ObjectMapper mapper) {

		Assert.notNull(mapper, "ObjectMapper must not be null!");

		JacksonMappingProvider provider = new JacksonMappingProvider(mapper);
		JsonProjectingMethodInterceptorFactory interceptorFactory = new JsonProjectingMethodInterceptorFactory(provider);

		SpelAwareProxyProjectionFactory factory = new SpelAwareProxyProjectionFactory();
		factory.registerMethodInvokerFactory(interceptorFactory);

		this.projectionFactory = factory;
		this.delegate = new DelegatableStringJsonMessageConverter(mapper);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.kafka.support.converter.MessagingMessageConverter#extractAndConvertValue(org.apache.kafka.clients.consumer.ConsumerRecord, java.lang.reflect.Type)
	 */
	@Override
	protected Object extractAndConvertValue(ConsumerRecord<?, ?> record, Type type) {

		Object value = record.value();

		if (value == null) {
			return KafkaNull.INSTANCE;
		}

		Class<?> rawType = ResolvableType.forType(type).resolve(Object.class);

		if (!rawType.isInterface()) {
			return this.delegate.extractAndConvertValue(record, type);
		}

		InputStream inputStream = new ByteArrayInputStream(getAsByteArray(value));

		return this.projectionFactory.createProjection(rawType, inputStream);
	}

	/**
	 * Returns the given source value as byte array.
	 *
	 * @param source must not be {@literal null}.
	 * @return the source instance as byte array.
	 */
	private static byte[] getAsByteArray(Object source) {

		Assert.notNull(source, "Source must not be null!");

		if (source instanceof String) {
			return String.class.cast(source).getBytes(StandardCharsets.UTF_8);
		}

		if (source instanceof byte[]) {
			return byte[].class.cast(source);
		}

		throw new ConversionException(String.format("Unsupported payload type %s!", source.getClass()), null);
	}

	/**
	 * @author Oliver Gierke
	 */
	private static class DelegatableStringJsonMessageConverter extends StringJsonMessageConverter {

		/**
		 * @param objectMapper
		 */
		private DelegatableStringJsonMessageConverter(ObjectMapper objectMapper) {
			super(objectMapper);
		}

		/* 
		 * (non-Javadoc)
		 * @see org.springframework.kafka.support.converter.StringJsonMessageConverter#extractAndConvertValue(org.apache.kafka.clients.consumer.ConsumerRecord, java.lang.reflect.Type)
		 */
		@Override
		public Object extractAndConvertValue(ConsumerRecord<?, ?> record, Type type) {
			return super.extractAndConvertValue(record, type);
		}
	}
}
