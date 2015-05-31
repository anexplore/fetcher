package com.fd.fetcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.HostnameVerifier;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.util.PublicSuffixMatcher;
import org.apache.http.conn.util.PublicSuffixMatcherLoader;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.ByteArrayBuffer;
import org.apache.http.util.EntityUtils;

import com.fd.dnscache.DnsCache;

/**
 * HttpFetcher 提供HttpGet HttpPost方法 自动解压gzip 设置KeepAlive,proxy,redirect,dnscache
 * 
 * @author caoliuyi
 *
 */
public class HttpFetcher {
	private volatile boolean inited = false;
	private boolean keepAlive = false;
	private boolean allowCookies = false;
	private boolean autoRedirect = true;
	private String cookieSpecs = CookieSpecs.IGNORE_COOKIES;
	private String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_3)"
			+ " AppleWebKit/537.36 (KHTML, like Gecko) Chrome/40.0.2214.111 Safari/537.36";
	private String proxyHost;
	private int proxyPort = 80;
	private int maxConnTotal = 100;
	private int maxConnPerRoute = 10;
	private int connectTimeout = 60000;
	private int readTimeout = 60000;
	private int retryCount = 1;
	private int maxBodyLength = 1024 * 1024;
	private int maxCacheCount = 10000;
	private int maxRedirect = 4;

	private CloseableHttpClient client;
	private RequestConfig noRedirectCfg;
	private RequestConfig defaultRequestCfg;
	private HttpHost proxy;
	private DnsCache dnsCache;

	/**
	 * Before use HttpFetcher must init it first
	 */
	public void init() {
		dnsCache = new DnsCache("dnsCache", maxCacheCount);
		defaultRequestCfg = RequestConfig.custom().setCookieSpec(cookieSpecs)
				.setConnectTimeout(connectTimeout)
				.setSocketTimeout(readTimeout).build();
		noRedirectCfg = RequestConfig.copy(defaultRequestCfg)
				.setRedirectsEnabled(false).build();
		HttpClientBuilder builder = HttpClients.custom();
		builder.setDefaultRequestConfig(defaultRequestCfg);
		LayeredConnectionSocketFactory sslSocketFactory = null;
		PublicSuffixMatcher publicSuffixMatcherCopy = PublicSuffixMatcherLoader.getDefault();
		if (sslSocketFactory == null) {
			HostnameVerifier hostnameVerifier = new DefaultHostnameVerifier(publicSuffixMatcherCopy);
			sslSocketFactory = new SSLConnectionSocketFactory(
					SSLContexts.createDefault(), hostnameVerifier);
		}
		final PoolingHttpClientConnectionManager poolingmgr = new PoolingHttpClientConnectionManager(
				RegistryBuilder
						.<ConnectionSocketFactory> create()
						.register("http",
								PlainConnectionSocketFactory.getSocketFactory())
						.register("https", sslSocketFactory).build(),
				new DnsResolverWithCache(dnsCache));
		if (maxConnTotal > 0) {
			poolingmgr.setMaxTotal(maxConnTotal);
		}
		if (maxConnPerRoute > 0) {
			poolingmgr.setDefaultMaxPerRoute(maxConnPerRoute);
		}
		if (keepAlive) {
			builder.setKeepAliveStrategy(DefaultConnectionKeepAliveStrategy.INSTANCE);
		} else {
			builder.setConnectionReuseStrategy(new ConnectionReuseStrategy() {
				public boolean keepAlive(HttpResponse arg0, HttpContext arg1) {
					return false;
				}
			});
		}
		builder.setConnectionManager(poolingmgr);
		builder.setRetryHandler(new DefaultHttpRequestRetryHandler(retryCount,
				false));
		builder.setProxy(proxy);
		builder.setMaxConnPerRoute(maxConnPerRoute);
		builder.setMaxConnTotal(maxConnTotal);
		builder.setUserAgent(userAgent);
		client = builder.build();
		inited = true;
	}

	public HttpResponseWrapper sendHttpHead(String url,
			Map<String, String> headers) throws ClientProtocolException,
			IOException {
		if (!inited || url == null) {
			throw new RuntimeException(
					"HttpFetcher instance has not been inited");
		}
		final HttpHead httphead = new HttpHead(url);
		if (headers != null) {
			for (Entry<String, String> entry : headers.entrySet()) {
				httphead.addHeader(entry.getKey(), entry.getValue());
			}
		}
		ResponseHandler<HttpResponseWrapper> responseHandler = new ResponseHandler<HttpResponseWrapper>() {
			public HttpResponseWrapper handleResponse(
					final HttpResponse response)
					throws ClientProtocolException, IOException {
				HttpResponseWrapper wrapper = new HttpResponseWrapper();
				wrapper.setHeaders(response.getAllHeaders());
				return wrapper;
			}
		};
		return client.execute(httphead, responseHandler);
	}

	/**
	 * HttpGet
	 * 
	 * @param url
	 * @param headers
	 * @return HttpResponseWrapper
	 * @throws Exception
	 */
	public HttpResponseWrapper sendHttpGet(String url,
			Map<String, String> headers) throws Exception {
		if (!inited || url == null) {
			throw new RuntimeException(
					"HttpFetcher instance has not been inited");
		}
		final HttpGet httpget = new HttpGet(url);
		if (headers != null) {
			for (Entry<String, String> entry : headers.entrySet()) {
				httpget.addHeader(entry.getKey(), entry.getValue());
			}
		}
		return sendRequest(httpget);
	}

	/**
	 * HttpPost
	 * 
	 * @param url
	 * @param data
	 * @param headers
	 * @return HttpResponseWrapper
	 * @throws Exception
	 */
	public HttpResponseWrapper sendHttpPost(String url,
			Map<String, String> data, Map<String, String> headers)
			throws Exception {
		if (!inited || url == null) {
			throw new RuntimeException(
					"HttpFetcher instance has not been inited");
		}
		final HttpPost httppost = new HttpPost(url);
		if (headers != null) {
			for (Entry<String, String> entry : headers.entrySet()) {
				httppost.addHeader(entry.getKey(), entry.getValue());
			}
		}
		if (data != null) {
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			for (Entry<String, String> entry : data.entrySet()) {
				params.add(new BasicNameValuePair(entry.getKey(), entry
						.getValue()));
			}
			httppost.setEntity(new UrlEncodedFormEntity(params, "utf-8"));
		}
		return sendRequest(httppost);
	}

	private HttpResponseWrapper sendRequest(final HttpRequestBase request)
			throws ClientProtocolException, IOException {
		final HttpResponseWrapper finalWrapper = new HttpResponseWrapper();
		ResponseHandler<HttpResponseWrapper> responseHandler = new ResponseHandler<HttpResponseWrapper>() {
			public HttpResponseWrapper handleResponse(
					final HttpResponse response)
					throws ClientProtocolException, IOException {
				HttpResponseWrapper wrapper = new HttpResponseWrapper();
				int status = response.getStatusLine().getStatusCode();
				if (status >= 200 && status < 300) {
					HttpEntity entity = response.getEntity();
					if (entity == null) {
						return null;
					}
					if (!autoRedirect) {
						wrapper.needRedirect = false;
					}
					if (entity.getContentLength() > maxBodyLength) {
						EntityUtils.consumeQuietly(response.getEntity());
						throw new RuntimeException("body content too long:"
								+ request.getURI().toString());
					}
					wrapper.setHeaders(response.getAllHeaders());
					byte[] bytes = toByteArray(entity);
					wrapper.setContentLength(bytes.length);
					ContentType contentType = ContentType.get(entity);
					if (contentType != null) {
						Charset charset = contentType.getCharset();
						if (charset != null) {
							charset = gb2312ToGBK(charset);
							wrapper.setContent(new String(bytes, charset));
							return wrapper;
						}
					}
					String charsetString = CharsetDetector.getCharset(bytes);
					if (charsetString.equalsIgnoreCase("GB2312")) {
						charsetString = "GBK";
					}
					wrapper.setContent(new String(bytes, charsetString));
					return wrapper;
				} else if (!autoRedirect
						&& HttpRedirectTool.isRedirected(request, response)) {
					EntityUtils.consumeQuietly(response.getEntity());
					HttpUriRequest nq = HttpRedirectTool.getRedirect(
							finalWrapper.getLastRequest(), response);
					if (nq != null) {
						wrapper.needRedirect = true;
						wrapper.lastRequest = nq;
					} else {
						wrapper = null;
					}
					return wrapper;
				} else {
					EntityUtils.consumeQuietly(response.getEntity());
					throw new ClientProtocolException(
							"Unexpected response status: "
									+ status);
				}
			}

		};
		if (!autoRedirect) {
			request.setConfig(this.noRedirectCfg);
			finalWrapper.lastRequest = request;
			int counter = 0;
			for (; counter < maxRedirect; counter++) {
				HttpResponseWrapper res = client.execute(
						finalWrapper.lastRequest, responseHandler);
				if (res == null) {
					break;
				}
				if (!res.needRedirect) {
					if (counter > 0) {
						res.realUrl = finalWrapper.lastRequest.getRequestLine()
								.getUri();
					}
					res.lastRequest = null;
					return res;
				}
				finalWrapper.lastRequest = res.lastRequest;
				if (finalWrapper.lastRequest instanceof HttpGet) {
					((HttpGet) finalWrapper.lastRequest)
							.setConfig(this.noRedirectCfg);
				}
				for (Header h : request.getAllHeaders()) {
					finalWrapper.lastRequest.addHeader(h);
				}
			}
		} else {
			return client.execute(request, responseHandler);
		}
		return null;
	}

	/**
	 * @param entity
	 * @return
	 * @throws IOException
	 */
	private byte[] toByteArray(final HttpEntity entity) throws IOException {
		if (entity == null) {
			return null;
		}
		final InputStream instream = entity.getContent();
		if (instream == null) {
			return null;
		}
		if (entity.getContentLength() > maxBodyLength) {
			return null;
		}
		try {
			int i = (int) entity.getContentLength();
			if (i < 0) {
				i = 4096;
			}
			final ByteArrayBuffer buffer = new ByteArrayBuffer(i);
			final byte[] tmp = new byte[4096];
			int l;
			while ((l = instream.read(tmp)) != -1) {
				if (buffer.length() >= maxBodyLength) {
					throw new IOException("entity too long");
				}
				buffer.append(tmp, 0, l);
			}
			return buffer.toByteArray();
		} finally {
			instream.close();
		}
	}

	/**
	 * change gb2312 to Gbk
	 * 
	 * @param src
	 * @return charset
	 */
	private Charset gb2312ToGBK(Charset src) {
		if (src.name().equalsIgnoreCase("GB2312")) {
			return Charset.forName("GBK");
		}
		return src;
	}

	/**
	 * close http client
	 * 
	 * @throws IOException
	 */
	public void close() throws IOException {
		inited = false;
		if (client != null) {
			client.close();
		}
	}

	public void setProxy(String host, int port) {
		proxyHost = host;
		proxyPort = port;
		proxy = new HttpHost(proxyHost, proxyPort);
	}

	public boolean isKeepAlive() {
		return keepAlive;
	}

	public void setKeepAlive(boolean keepAlive) {
		this.keepAlive = keepAlive;
	}

	public boolean isAllowCookies() {
		return allowCookies;
	}

	public void setAllowCookies(boolean allowCookies) {
		this.allowCookies = allowCookies;
		if (this.allowCookies) {
			cookieSpecs = CookieSpecs.STANDARD;
		}
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}

	public int getMaxConnTotal() {
		return maxConnTotal;
	}

	public void setMaxConnTotal(int maxConnTotal) {
		this.maxConnTotal = maxConnTotal;
	}

	public int getMaxConnPerRoute() {
		return maxConnPerRoute;
	}

	public void setMaxConnPerRoute(int maxConnPerRoute) {
		this.maxConnPerRoute = maxConnPerRoute;
	}

	public int getConnectTimeout() {
		return connectTimeout;
	}

	public void setConnectTimeout(int connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public int getReadTimeout() {
		return readTimeout;
	}

	public void setReadTimeout(int readTimeout) {
		this.readTimeout = readTimeout;
	}

	public int getRetryCount() {
		return retryCount;
	}

	public void setRetryCount(int retryCount) {
		this.retryCount = retryCount;
	}

	public int getMaxBodyLength() {
		return maxBodyLength;
	}

	public void setMaxBodyLength(int maxBodyLength) {
		this.maxBodyLength = maxBodyLength;
	}

	public int getMaxCacheCount() {
		return maxCacheCount;
	}

	public void setMaxCacheCount(int maxCacheCount) {
		this.maxCacheCount = maxCacheCount;
	}

	public DnsCache getDnsCache() {
		return dnsCache;
	}

	public void setDnsCache(DnsCache dnsCache) {
		this.dnsCache = dnsCache;
	}

	public String getProxyHost() {
		return proxyHost;
	}

	public void setProxyHost(String proxyHost) {
		this.proxyHost = proxyHost;
	}

	public int getProxyPort() {
		return proxyPort;
	}

	public void setProxyPort(int proxyPort) {
		this.proxyPort = proxyPort;
	}

	public HttpHost getProxy() {
		return proxy;
	}

	public void setProxy(HttpHost proxy) {
		this.proxy = proxy;
	}

	public String getCookieSpecs() {
		return cookieSpecs;
	}

	public boolean isAutoRedirect() {
		return autoRedirect;
	}

	public void setAutoRedirect(boolean autoRedirect) {
		this.autoRedirect = autoRedirect;
	}

	public int getMaxRedirect() {
		return maxRedirect;
	}

	public void setMaxRedirect(int maxRedirect) {
		this.maxRedirect = maxRedirect;
	}

}
