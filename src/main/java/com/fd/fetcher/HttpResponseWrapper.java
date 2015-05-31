package com.fd.fetcher;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.client.methods.HttpUriRequest;
/**
 * HttpResponse result
 * @author caoliuyi
 *
 */

public class HttpResponseWrapper {
	public boolean needRedirect = false;
	public HttpUriRequest lastRequest;
	public String realUrl = null;//last redirect url
	public String content = null;//html content
	public Header[] headers = null;//all headers of real url
	public long contentLength = 0;
	public long headerLength = 0;

	public Header[] getAllHeader() {
		return headers;
	}

	public void setHeaders(Header[] allHeader) {
		headers = new Header[allHeader.length];
		System.arraycopy(allHeader, 0, headers, 0, allHeader.length);
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public long getContentLength() {
		return contentLength;
	}

	public void setContentLength(long contentLength) {
		this.contentLength = contentLength;
	}

	public HttpUriRequest getLastRequest() {
		return lastRequest;
	}

	public void setLastRequest(HttpUriRequest lastRequest) {
		this.lastRequest = lastRequest;
	}

	public long getHeaderLength() {
		return headerLength;
	}

	public String getRealUrl() {
		return realUrl;
	}

	public void setRealUrl(String realUrl) {
		this.realUrl = realUrl;
	}

	public List<String> getHeader(String key) {
		if (headers == null || key == null) {
			return null;
		}
		List<String> res = new ArrayList<String>();
		for (int i = 0; i < headers.length; i++) {
			Header header = headers[i];
			if (header.getName().equalsIgnoreCase(key)) {
				res.add(header.getValue());
			}
		}
		return res;
	}
}
