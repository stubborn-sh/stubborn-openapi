/*
 * Copyright 2026-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.contract.verifier.openapivalidation;

import java.util.Locale;

import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.spec.internal.Header;
import org.springframework.cloud.contract.spec.internal.Headers;
import org.springframework.cloud.contract.spec.internal.QueryParameter;
import org.springframework.cloud.contract.spec.internal.QueryParameters;
import org.springframework.cloud.contract.spec.internal.Request;
import org.springframework.cloud.contract.spec.internal.Response;
import org.springframework.cloud.contract.spec.internal.Url;

/**
 * Converts a {@link Contract} into a synthetic {@link SimpleRequest} +
 * {@link SimpleResponse} pair so the Atlassian {@code swagger-request-validator} can
 * validate it against an OpenAPI document.
 */
final class ContractHttpAdapter {

	/**
	 * Jackson {@code ObjectMapper} is thread-safe once configured — no further mutation.
	 */
	private static final ObjectMapper JSON = new ObjectMapper();

	private ContractHttpAdapter() {
		throw new AssertionError("Utility class");
	}

	static SimpleRequest toRequest(Contract contract, String method, String path) {
		SimpleRequest.Builder builder = new SimpleRequest.Builder(method.toUpperCase(Locale.ROOT), stripQuery(path));
		Request request = contract.getRequest();
		if (request == null) {
			return builder.build();
		}

		applyHeaders(request.getHeaders(), builder::withContentType, builder::withHeader);
		applyQueryParameters(builder, request);
		applyRequestBody(builder, request);
		return builder.build();
	}

	static SimpleResponse toResponse(Contract contract, int status) {
		SimpleResponse.Builder builder = new SimpleResponse.Builder(status);
		Response response = contract.getResponse();
		if (response == null) {
			return builder.build();
		}

		applyHeaders(response.getHeaders(), builder::withContentType, builder::withHeader);
		applyResponseBody(builder, response);
		return builder.build();
	}

	private static void applyHeaders(@Nullable Headers headers, ContentTypeSink contentTypeSink,
			HeaderSink headerSink) {
		if (headers == null || headers.getEntries() == null) {
			return;
		}
		for (Header header : headers.getEntries()) {
			Object value = DslValueExtractor.unwrap(header);
			if (value == null) {
				continue;
			}
			String stringValue = value.toString();
			if ("Content-Type".equalsIgnoreCase(header.getName())) {
				contentTypeSink.accept(stringValue);
			}
			else {
				headerSink.accept(header.getName(), stringValue);
			}
		}
	}

	private static void applyQueryParameters(SimpleRequest.Builder builder, Request request) {
		Url url = request.getUrl() != null ? request.getUrl() : request.getUrlPath();
		if (url == null) {
			return;
		}
		QueryParameters params = url.getQueryParameters();
		if (params == null || params.getParameters() == null) {
			return;
		}
		for (QueryParameter param : params.getParameters()) {
			Object value = DslValueExtractor.unwrap(param);
			if (value != null) {
				builder.withQueryParam(param.getName(), value.toString());
			}
		}
	}

	private static void applyRequestBody(SimpleRequest.Builder builder, Request request) {
		if (request.getBody() == null) {
			return;
		}
		String body = serializeBody(DslValueExtractor.unwrap(request.getBody()));
		if (body != null) {
			builder.withBody(body);
		}
	}

	private static void applyResponseBody(SimpleResponse.Builder builder, Response response) {
		if (response.getBody() == null) {
			return;
		}
		String body = serializeBody(DslValueExtractor.unwrap(response.getBody()));
		if (body != null) {
			builder.withBody(body);
		}
	}

	@Nullable private static String serializeBody(@Nullable Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof String s) {
			return s;
		}
		if (value instanceof Number || value instanceof Boolean) {
			return value.toString();
		}
		try {
			return JSON.writeValueAsString(value);
		}
		catch (RuntimeException ex) {
			return value.toString();
		}
		catch (Exception ex) {
			return value.toString();
		}
	}

	private static String stripQuery(String path) {
		int q = path.indexOf('?');
		return q >= 0 ? path.substring(0, q) : path;
	}

	@FunctionalInterface
	private interface ContentTypeSink {

		void accept(String value);

	}

	@FunctionalInterface
	private interface HeaderSink {

		void accept(String name, String value);

	}

}
