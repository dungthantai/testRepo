/*
 * Copyright (C) 2024 Nguyễn Việt Dũng.
 * Email: dungthantai@gmail.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.example;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

public class HttpFlex {
	final static AtomicBoolean defaultDebug = new AtomicBoolean();
	boolean debug;

	static final AtomicReference<Gson> defaultGson = new AtomicReference<>(new Gson());
	Gson gson = null;

	final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
	final HttpClient.Builder clientBuilder = HttpClient.newBuilder();
	HttpRequest request;
	HttpResponse<?> httpResponse;

	/**
	 * Sets the default debug mode for all instances of HttpFlex.
	 *
	 * @param allowDebug true to enable debug mode, false to disable
	 */

	public static void defaultDebug(boolean allowDebug) {
		defaultDebug.set(allowDebug);
	}

	/**
	 * Enables or disables debug mode for this instance of HttpFlex.
	 *
	 * @param allowDebug true to enable debug mode, false to disable
	 * @return this
	 */

	public HttpFlex debug(boolean allowDebug) {
		debug = allowDebug;
		return this;
	}

	/**
	 * Sets the Gson instance to be used for JSON serialization and deserialization.
	 *
	 * @param gson the Gson instance to set
	 * @return this HttpFlex instance
	 */
	public HttpFlex setGson(Gson gson) {
		this.gson = gson;
		return this;
	}

	public Gson gson() {
		return (gson != null) ? gson : defaultGson.get();
	}

	/**
	 * Sets the default Gson instance to be used for JSON serialization and
	 * deserialization across all instances of HttpFlex. Use for
	 * {@link HttpFlex.Multipart} record too.
	 *
	 * @param gson the Gson instance to set as default
	 */

	public static void setDefaultGson(Gson gson) {
		defaultGson.set(gson);
	}

	/**
	 * Constructs a new HttpFlex instance with the specified URL.
	 *
	 * @param url the URL to connect to
	 */

	public HttpFlex(String url) {
		debug = defaultDebug.get();
		try {
			requestBuilder.uri(URI.create(url));
		} catch (IllegalArgumentException e) {
			System.out.println("HTTPCommand not allow: " + url);
			e.printStackTrace();
			requestBuilder.uri(URI.create("http://127.0.0.1"));
		}
	}

	/**
	 * Constructs a new HttpFlex instance with the specified URI.
	 *
	 * @param uri the URI to connect to
	 */

	public HttpFlex(URI uri) {
		debug = defaultDebug.get();
		requestBuilder.uri(uri);
	}

	/**
	 * Creates a new HttpFlex instance with the specified URL.
	 *
	 * @param url the URL to connect to
	 * @return a new HttpFlex instance
	 */
	public static HttpFlex instance(String url) {
		return new HttpFlex(url);
	}

	/**
	 * Creates a new HttpFlex instance with the specified URI.
	 *
	 * @param uri the URI to connect to
	 * @return a new HttpFlex instance
	 */
	public static HttpFlex instance(URI uri) {
		return new HttpFlex(uri);
	}


	/**
	 * Retrieves the HttpResponse from the last request made.
	 *
	 * @return the HttpResponse object representing the response
	 */
	public HttpResponse<?> readResponse() {
		return httpResponse;
	}

	/**
	 * Retrieves the response body as the specified class type.
	 *
	 * @param <R>   the type of the response object
	 * @param clazz the class of the response object (e.g., MyObject.class)
	 * @return the response object of the specified class
	 */
	private <R> R getResponse(Class<R> clazz) {
		if (debug) {
			System.out.println("\nFrom method: " + Thread.currentThread().getStackTrace()[3].getMethodName() + "\nRequest: " + request.uri().toString() + "\n");
		}
		try (HttpClient httpclient = clientBuilder.build()) {
			this.httpResponse = switch (clazz.getSimpleName()) {
				case "InputStream" -> clientBuilder.build().send(request, BodyHandlers.ofInputStream());
				case "byte[]" -> httpclient.send(request, BodyHandlers.ofByteArray());
				default -> httpclient.send(request, BodyHandlers.ofString());
			};

			if (debug) {
				System.out.println(switch (httpResponse.body()) {
					case InputStream inputStream ->
							"Response Body: InputStream " + (inputStream.available() / 1024) + "KB\n";
					case byte[] bytes -> "Response Body: Bytes " + (bytes.length / 1024) + "KB\n";
					default -> "Response Body: " + httpResponse.body().toString() + "\n";
				});
			}

			if (clazz.equals(InputStream.class) || clazz.equals(byte[].class) || clazz.equals(String.class)) {
				return clazz.cast(httpResponse.body());
			} else {
				return gson().fromJson(httpResponse.body().toString(), clazz);
			}

		} catch (JsonParseException | IOException | InterruptedException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Retrieves the response body as the specified type.
	 *
	 * @param type the type of the response object (e.g., new TypeToken<Map<String,
	 *             MyObject>>() {}.getType())
	 * @return the response object of the specified type
	 */

	private <R> R getResponse(Type type) {
		return gson().fromJson(getResponse(String.class), type);
	}

	/**
	 * Sets the HTTP request body based on the specified request body object.
	 *
	 * @param <T>         the type of the request body
	 * @param requestBody the request body object
	 */
	private <T> void setRequest(T requestBody) {
		setRequest("POST", requestBody);
	}

	/**
	 * Sets the HTTP request body based on the specified request body object and
	 * HTTP method.
	 *
	 * @param <T>         the type of the request body
	 * @param method      the HTTP method (e.g., GET, POST, PUT, DELETE)
	 * @param requestBody the request body object
	 */
	private <T> void setRequest(String method, T requestBody) {
		method = method.toUpperCase();
		if (debug && requestBody != null) {
			System.out.println("\nRequest Body : " + (requestBody instanceof String ? requestBody : (requestBody.toString().length() > 200 ? requestBody.toString().substring(0, 200) + "..." : requestBody.toString())));
		}
		request = requestBuilder.method(method, switch (requestBody) {
			case null -> BodyPublishers.noBody();
			case String s -> BodyPublishers.ofString(s, StandardCharsets.UTF_8);
			case InputStream is -> BodyPublishers.ofInputStream(() -> is);
			case byte[] b -> BodyPublishers.ofByteArray(b);
			case Multipart m -> {
				requestBuilder.headers(ContentType.MULTIPART(m.boundary).headerValues());
				yield BodyPublishers.ofByteArray(m.build());
			}
			case UrlEncoded u -> {
				requestBuilder.headers(ContentType.URLENC.headerValues());
				yield BodyPublishers.ofString(u.build());
			}
			default -> {
				requestBuilder.headers(ContentType.JSON.headerValues());
				yield BodyPublishers.ofString(gson().toJson(requestBody), StandardCharsets.UTF_8);
			}
		}).build();
	}

	/**
	 * Enumeration of HTTP Content-Types for setting request headers.
	 */
	public enum ContentType {
		TEXT("text/plain"), URLENC("application/x-www-form-urlencoded"), JSON("application/json"), XML("application/xml"), MP3("audio/mp3"), MP4("video/mp4"), OCTET("application/octet-stream"), CUSTOM(null);

		private final String type = "Content-Type";
		private String value;

		ContentType(String value) {
			this.value = value;
		}

		public String[] headerValues() {
			return new String[]{type, value};
		}

		public String description() {
			return type + ": " + value;
		}

		/**
		 * Sets the specify ContentType I not ready write in code
		 *
		 * @param content_type the HTTP Content Type (e.g., "application/json")
		 */
		public static ContentType custom(String content_type) {
			CUSTOM.value = content_type;
			return CUSTOM;
		}

		/**
		 * Sets the ContentType for multipart-formdata
		 *
		 * @param boundary the multipart-formdata
		 */
		public static ContentType MULTIPART(String boundary) {
			CUSTOM.value = "multipart/form-data; boundary=" + boundary;
			return CUSTOM;
		}
	}

	/**
	 * GET data from http server.
	 *
	 * @param <R>   the type of the response body
	 * @param clazz type is Class need to cast
	 * @return object have the same Class of clazz
	 */
	public <R> R get(Class<R> clazz) {
		request = requestBuilder.GET().build();
		return getResponse(clazz);
	}

	/**
	 * GET data from http server.
	 *
	 * @param type is Class need to cast
	 * @return object have the same Class of type
	 */
	public <R> R get(Type type) {
		request = requestBuilder.GET().build();
		return getResponse(type);
	}

	/**
	 * GET data from http server.
	 *
	 * @return {@link String}
	 */
	public String get() {
		return get(String.class);
	}

	/**
	 * POST data to http server.
	 *
	 * @param <T>         the type of the request body
	 * @param <R>         the type of the response body
	 * @param requestBody is object to send, allow All type of class,
	 *                    {@link InputStream}, {@link Path} and byteArray will send
	 *                    data the same way, {@link HttpFlex.Multipart} and
	 *                    {@link HttpFlex.UrlEncoded} will auto generate to send,
	 *                    {@link String} and all other Object class with send as
	 *                    String (as Json String if is an other Object, use
	 *                    {@link HttpFlex#gson})
	 * @param clazz       is Class need to cast, use {@link HttpFlex#gson}
	 * @return same Class of <R>
	 */
	public <T, R> R post(T requestBody, Class<R> clazz) {
		setRequest(requestBody);
		return getResponse(clazz);
	}

	/**
	 * POST data to http server.
	 *
	 * @param <T>         the type of the request body
	 * @param <R>         the type of the response body
	 * @param requestBody is object to send, allow All type of class,
	 *                    {@link InputStream}, {@link Path} and byteArray will send
	 *                    data the same way, {@link HttpFlex.Multipart} and
	 *                    {@link HttpFlex.UrlEncoded} will auto generate to send,
	 *                    {@link String} and all other Object class with send as
	 *                    String (as Json String if is an other Object, use
	 *                    {@link HttpFlex#gson})
	 * @param type        is Class need to cast, use {@link HttpFlex#gson}
	 * @return same Class of type
	 */
	public <T, R> R post(T requestBody, Type type) {
		setRequest(requestBody);
		return getResponse(type);
	}

	/**
	 * POST data to http server.
	 *
	 * @param <T>         the type of the request body
	 * @param requestBody is object to send, allow All type of class,
	 *                    {@link InputStream}, {@link Path} and byteArray will send
	 *                    data the same way, {@link HttpFlex.Multipart} and
	 *                    {@link HttpFlex.UrlEncoded} will auto generate to send,
	 *                    {@link String} and all other Object class with send as
	 *                    String (as Json String if is an other Object, use
	 *                    {@link HttpFlex#gson})
	 * @return {@link String}
	 */
	public <T> String post(T requestBody) {
		return post(requestBody, String.class);
	}

	/**
	 * POST empty data to http server.
	 *
	 * @return {@link String}
	 */
	public <T> String post() {
		return post(null);
	}

	/**
	 * send DELETE to http server. same explain with {@link HttpFlex#get(Class)))}
	 */
	public <R> R delete(Class<R> clazz) {
		request = requestBuilder.DELETE().build();
		return getResponse(clazz);
	}

	/**
	 * send DELETE to http server. same explain with {@link HttpFlex#get(Type))}
	 */
	public <R> R delete(Type type) {
		request = requestBuilder.DELETE().build();
		return getResponse(type);
	}

	/**
	 * send DELETE to http server. same explain with {@link HttpFlex#get()}
	 */
	public String delete() {
		return delete(String.class);
	}

	/**
	 * send Custom method (eg., PUT, PATCH, OPTIONS, ...) to http server.
	 *
	 * @param method is String (eg., "put", "PUT", "patch", ...). All other same
	 *               explain with {@link HttpFlex#post(Object, Class)}
	 */
	public <T, R> R method(String method, T requestBody, Class<R> clazz) {
		setRequest(method, requestBody);
		return getResponse(clazz);
	}

	/**
	 * send Custom method (eg., PUT, PATCH, OPTIONS, ...) to http server.
	 *
	 * @param method is String (eg., "put", "PUT", "patch", ...). All other same
	 *               explain with {@link HttpFlex#post(Object, Type)}
	 */
	public <T, R> R method(String method, T requestBody, Type type) {
		setRequest(method, requestBody);
		return getResponse(type);
	}

	/**
	 * send Custom method (eg., PUT, PATCH, OPTIONS, ...) to http server.
	 *
	 * @param method is String (eg., "put", "PUT", "patch", ...). All other same
	 *               explain with {@link HttpFlex#post(Object)}
	 */
	public <T> String method(String method, T requestBody) {
		return method(method, requestBody, String.class);
	}

	/**
	 * send Custom method (eg., PUT, PATCH, OPTIONS, ...) to http server.
	 *
	 * @param method is String (eg., "put", "PUT", "patch", ...). All other same
	 *               explain with {@link HttpFlex#post()}
	 */
	public <T> String method(String method) {
		return method(method, null, String.class);
	}

	/**
	 * Send multiple action with instance
	 *
	 * @param consumer is Consumer<HttpFlex>
	 */
	public HttpFlex execute(Consumer<HttpFlex> consumer) {
		consumer.accept(this);
		return this;
	}

	/**
	 * Send multiple action with instance
	 *
	 * @param <R>      return type
	 * @param function Function<HttpFlex, R>
	 */

	public <R> R compute(Function<HttpFlex, R> function) {
		return function.apply(this);
	}

	/**
	 * Sets the proxy authentication credentials for HTTP requests.
	 *
	 * @param username The username for proxy authentication.
	 * @param password The password for proxy authentication.
	 */
	public HttpFlex proxyAuth(String username, String password) {
		requestBuilder.headers("Proxy-Authorization", "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8)));
		return this;
	}

	/**
	 * Sets the proxy server details for HTTP requests.
	 *
	 * @param ip   The IP address of the proxy server.
	 * @param port The port number of the proxy server.
	 */
	public HttpFlex proxy(String ip, Integer port) {
		clientBuilder.proxy(ProxySelector.of(new InetSocketAddress(ip, port)));
		return this;
	}

	/**
	 * Sets custom headers for HTTP requests.
	 *
	 * @param keyValue An array of key-value pairs representing headers.
	 * @return This {@code HttpFlex} instance for method chaining.
	 */
	public HttpFlex header(String... keyValue) {
		requestBuilder.headers(keyValue);
		return this;
	}

	/**
	 * Sets a single header for HTTP requests.
	 *
	 * @param name  The name of the header.
	 * @param value The value of the header.
	 * @return This {@code HttpFlex} instance for method chaining.
	 */
	public HttpFlex header(String name, String value) {
		requestBuilder.header(name, value);
		return this;
	}

	/**
	 * Sets a single header for HTTP requests.
	 *
	 * @param token The value of the bearer access token.
	 * @return This {@code HttpFlex} instance for method chaining.
	 */
	public HttpFlex bearer(String token) {
		requestBuilder.header("Authorization", "Bearer " + token);
		return this;
	}

	/**
	 * Sets the Content-Type header for HTTP requests.
	 *
	 * @param type The {@code ContentType} enum value representing the xpathContent type.
	 * @return This {@code HttpFlex} instance for method chaining.
	 */
	public HttpFlex header(ContentType type) {
		requestBuilder.headers(type.headerValues());
		return this;
	}

	/**
	 * Representation of a multipart-formdata request, use to send in
	 * {@link HttpFlex#post(Object)}.
	 */
	public record Multipart(ByteArrayOutputStream requestBody, String boundary, Map<String, Object> data,
	                        AtomicBoolean needRebuild) implements AutoCloseable {
		/**
		 * Constructs a new Multipart instance with a randomly generated boundary.
		 */
		public static Multipart instance() {
			return instance("-FormBoundary" + Long.toHexString(System.currentTimeMillis()));
		}

		/**
		 * Constructs a new Multipart instance with a randomly generated boundary. And
		 * put chunk of data to instance form-multipart.
		 *
		 * @param map Map.Entry Key is multipart-formdata name
		 *            Map.Entry Value is multipart-formdata data (can be a {@link String},
		 *            {@link InputStream}, byte[], or any other class will be
		 *            automatically convert to Json string by
		 *            {@link HttpFlex#defaultGson})
		 */
		public static Multipart instance(Map<String, ?> map) {
			return instance("-FormBoundary" + Long.toHexString(System.currentTimeMillis())).putAll(map);
		}

		/**
		 * Constructs a new multipart-formdata instance with a custom boundary.
		 *
		 * @param boundary is multipart-formdata boundary
		 */
		public static Multipart instance(String boundary) {
			return new Multipart(new ByteArrayOutputStream(), boundary, new LinkedHashMap<>(), new AtomicBoolean(true));
		}

		/**
		 * Constructs a new multipart-formdata instance with a custom boundary. And put
		 * chunk of data to instance form-multipart.
		 *
		 * @param boundary is multipart-formdata boundary
		 * @param map      Map.Entry Key is multipart-formdata name
		 *                 Map.Entry Value is multipart-formdata data (can be a {@link String},
		 *                 {@link InputStream}, byte[], or any other class will be
		 *                 automatically convert to Json string by
		 *                 {@link HttpFlex#defaultGson})
		 */
		public static Multipart instance(String boundary, Map<String, ?> map) {
			return instance(boundary).putAll(map);
		}

		/**
		 * Put chunk of data to current form-multipart.
		 *
		 * @param map Map.Entry Key is multipart-formdata name
		 *            Map.Entry Value is multipart-formdata data (can be a {@link String},
		 *            {@link InputStream}, byte[], or any other class will be
		 *            automatically convert to Json string by
		 *            {@link HttpFlex#defaultGson})
		 */
		public <T> Multipart putAll(Map<String, ?> map) {
			data.putAll(map);
			needRebuild.set(true);
			return this;
		}

		/**
		 * Put data to current form-multipart.
		 *
		 * @param name is multipart-formdata name
		 * @param body is multipart-formdata data (can be a {@link String},
		 *             {@link InputStream}, byte[], or any other class will be
		 *             automatically convert to Json string by
		 *             {@link HttpFlex#defaultGson})
		 */
		public <T> Multipart put(String name, T body) {
			data.put(name, body);
			needRebuild.set(true);
			return this;
		}

		void writeRequest(String value) {
			try {
				Channels.newChannel(requestBody).write(StandardCharsets.UTF_8.encode(value));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		void writeChunkRequest(byte[] bytes) {
			if (bytes != null) {
				int size = bytes.length;
				int offset = 0;
				int DefaultBufferSize = 128 * 1024;
				while (size > 0) {
					final int buffer = Math.min(size, DefaultBufferSize);
					requestBody.write(bytes, offset, buffer);
					size -= buffer;
					offset += buffer;
				}
			}
		}

		byte[] build() {
			if (needRebuild.compareAndSet(true, false)) {
				data.forEach((name, body) -> {
					writeRequest("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"");
					writeRequest(switch (body) {
						case Path ignored -> "; filename=\"" + name + "\"\r\n" + ContentType.OCTET.description();
						case InputStream ignored -> "; filename=\"" + name + "\"\r\n" + ContentType.OCTET.description();
						case byte[] ignored -> "; filename=\"" + name + "\"\r\n" + ContentType.OCTET.description();
						case String ignored -> "";
						default -> "\r\n" + ContentType.JSON.description();
					});
					writeRequest("\r\n\r\n");

					try {
						writeChunkRequest(switch (body) {
							case Path path -> Files.readAllBytes(path);
							case InputStream inputStream -> inputStream.readAllBytes();
							case byte[] bytes -> bytes;
							case String string -> string.getBytes();
							default -> defaultGson.get().toJson(body).getBytes();
						});
					} catch (IOException e) {
						e.printStackTrace();
					}
					writeRequest("\r\n");
				});
				writeRequest("--" + boundary + "--\r\n");
			}
			return requestBody.toByteArray();
		}

		/**
		 * Re-use instance with empty variable
		 */
		public void reset() {
			requestBody.reset();
			data.clear();
		}

		public void close() {
			data.clear();
			try {
				requestBody.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Representation of a form-urlencoded request, use to send in
	 * {@link HttpFlex#post(Object)}.
	 */
	public record UrlEncoded(StringBuilder requestBody, Map<String, Object> data,
	                         AtomicBoolean needRebuild) implements AutoCloseable {
		/**
		 * Constructs a new form-urlencoded instance.
		 */
		public static UrlEncoded instance() {
			return new UrlEncoded(new StringBuilder(), new LinkedHashMap<>(), new AtomicBoolean(true));
		}

		/**
		 * Put chunk of data to current form-urlencoded.
		 *
		 * @param map Map.Entry Key is form-urlencoded name
		 *            Map.Entry Value is form-urlencoded data (can be a {@link String},
		 *            {@link InputStream}, byte[], or any other class will be
		 *            automatically convert to Json string by
		 *            {@link HttpFlex#defaultGson})
		 */
		public <T> UrlEncoded putAll(Map<String, ?> map) {
			data.putAll(map);
			needRebuild.set(true);
			return this;
		}

		/**
		 * Put data to current form-urlencoded.
		 *
		 * @param name is form-urlencoded name
		 * @param body is form-urlencoded data (can be a {@link String},
		 *             {@link InputStream}, byte[], or any other class will be
		 *             automatically convert to Json string by
		 *             {@link HttpFlex#defaultGson})
		 */
		public <T> UrlEncoded put(String name, T body) {
			data.put(name, body);
			needRebuild.set(true);
			return this;
		}

		/**
		 * Remove data of current form-urlencoded.
		 *
		 * @param name is form-urlencoded name
		 */
		public UrlEncoded remove(String name) {
			data.remove(name);
			needRebuild.set(true);
			return this;
		}

		/**
		 * @param name is form-urlencoded name
		 * @param body is form-urlencoded data (can be a {@link String}, auto
		 *             encode-url)
		 */
		public <T> UrlEncoded putEncoded(String name, String body) {
			needRebuild.set(true);
			return put(name, URLEncoder.encode(body, StandardCharsets.UTF_8));
		}

		String inputStreamToString(final InputStream inputStream) {
			StringBuilder stringBuilder = new StringBuilder();
			byte[] buffer = new byte[1024];
			int bytesRead;
			try {
				while ((bytesRead = inputStream.read(buffer)) != -1) {
					stringBuilder.append(new String(buffer, 0, bytesRead));
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			return stringBuilder.toString();
		}

		public String build() {
			if (needRebuild.compareAndSet(true, false)) {
				data.forEach((name, body) -> {
					try {
						requestBody.append("&").append(name).append("=").append(switch (body) {
							case Path path -> Files.readString(path);
							case InputStream inputStream -> inputStreamToString(inputStream);
							case byte[] bytes -> Base64.getEncoder().encodeToString(bytes);
							case String string -> string;
							case Boolean b -> b.toString();
							case Integer i -> i.toString();
							case Long l -> l.toString();
							case Float d -> d.toString();
							case Double d -> d.toString();
							default -> defaultGson.get().toJson(body);
						});
					} catch (IOException e) {
						e.printStackTrace();
					}
				});
				requestBody.deleteCharAt(0);
			}
			return requestBody.toString();
		}

		/**
		 * Re-use instance with empty variable
		 */
		public void reset() {
			requestBody.setLength(0);
			data.clear();
		}

		/**
		 * Remove intsance
		 */
		public void close() {
			requestBody.setLength(0);
			data.clear();
		}
	}

	/**
	 * @param fulltext is String mix Json and normal text
	 * @return only "{...}" part as String
	 */

	public static String getJsonstring(String fulltext) {
		int start = fulltext.indexOf("{");
		int end = fulltext.lastIndexOf("}");
		return fulltext.substring(start, end + 1);
	}

	/**
	 * @param uri {@link URI}
	 * @return Map<Parameter Name, Parameter Value>
	 */
	public static Map<String, String> getQueryParams(URI uri) {
		return Arrays.stream(uri.getQuery().split("&")).map(param -> param.split("=")).collect(Collectors.toMap(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8), pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
	}

	/**
	 * Use for download file from http server
	 *
	 * @param uri {@link URI}
	 * @return {@link InputStream}
	 */
	public static InputStream getFileInputStream(URI uri) {
		return new HttpFlex(uri).get(InputStream.class);
	}

	/**
	 * Use for download file from http server
	 *
	 * @param uri {@link URI}
	 * @return byte array
	 */
	public static byte[] getFileBytes(URI uri) {
		return new HttpFlex(uri).get(byte[].class);
	}

	/**
	 * Use for download file from http server
	 *
	 * @param uri {@link URI}
	 * @return {@link Path}
	 */
	public static Path getFile(URI uri, Path path) {
		try {
			if (!Files.isRegularFile(path)) {
				Files.write(path, getFileBytes(uri), StandardOpenOption.CREATE_NEW);
			}
			return path;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Use for download multiple files from http server
	 *
	 * @param uriList {@link List<URI>}
	 * @return {@link List<InputStream>}
	 */
	public static List<InputStream> getFilesInputStreamFromURI(List<URI> uriList) {
		return uriList.stream().map(HttpFlex::getFileInputStream).filter(Objects::nonNull).toList();
	}

	/**
	 * Use for download multiple files from http server
	 *
	 * @param uriList {@link List<URI>}
	 * @return {@link List<byte[]>}
	 */
	public static List<byte[]> getFilesBytesFromURI(List<URI> uriList) {
		return uriList.stream().map(HttpFlex::getFileBytes).filter(Objects::nonNull).toList();
	}

	/**
	 * Use for download multiple files from http server
	 *
	 * @param uriPathMap {@link Map<URI, Path>}
	 * @return {@link List<Path>}
	 */
	public static List<Path> getFilesFromURI(Map<URI, Path> uriPathMap) {
		return uriPathMap.entrySet().stream().map(entry -> getFile(entry.getKey(), entry.getValue())).filter(Objects::nonNull).toList();
	}

	/**
	 * Use for download file from http server
	 *
	 * @param url {@link String}
	 * @return {@link InputStream}
	 */
	public static InputStream getFileInputStream(String url) {
		if (url.startsWith("data:image")) {
			return new ByteArrayInputStream(Base64.getDecoder().decode(url.split(",")[1]));
		} else {
			return getFileInputStream(URI.create(url));
		}
	}

	/**
	 * Use for download file from http server
	 *
	 * @param url {@link String}
	 * @return byteArray
	 */
	public static byte[] getFileBytes(String url) {
		try {
			if (url.startsWith("data:image")) {
				return Base64.getDecoder().decode(url.split(",")[1]);
			} else {
				return getFileBytes(URI.create(url));
			}
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Use for download file from http server
	 *
	 * @param url {@link String}
	 * @return {@link Path}
	 */
	public static Path getFile(String url, Path destinationPath) {
		try {
			if (!Files.isRegularFile(destinationPath)) {
				Files.write(destinationPath, getFileBytes(url), StandardOpenOption.CREATE_NEW);
			}
			return destinationPath;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Use for download multiple files from http server
	 *
	 * @param urlList {@link List<String>}
	 * @return {@link List<InputStream>}
	 */
	public static List<InputStream> getFilesInputStream(List<String> urlList) {
		return urlList.stream().map(HttpFlex::getFileInputStream).filter(Objects::nonNull).toList();
	}

	/**
	 * Use for download multiple files from http server
	 *
	 * @param urlList {@link List<String>}
	 * @return {@link List<byte[]>}
	 */
	public static List<byte[]> getFilesBytes(List<String> urlList) {
		return urlList.stream().map(HttpFlex::getFileBytes).filter(Objects::nonNull).toList();
	}

	/**
	 * Use for download multiple files from http server
	 *
	 * @param urlPathMap {@link List<String>}
	 * @return {@link List<Path>}
	 */
	public static List<Path> getFiles(Map<String, Path> urlPathMap) {
		return urlPathMap.entrySet().stream().map(entry -> getFile(entry.getKey(), entry.getValue())).filter(Objects::nonNull).toList();
	}
}
