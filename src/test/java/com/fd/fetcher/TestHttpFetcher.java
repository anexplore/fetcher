package com.fd.fetcher;

import java.io.IOException;

import org.apache.http.Header;
import org.apache.http.client.ClientProtocolException;

import junit.framework.TestCase;

public class TestHttpFetcher extends TestCase {
	public HttpFetcher fetcher;
	public void setUp(){
		fetcher = new HttpFetcher();
		fetcher.init();
	}
	public void testHttpGet() throws IOException {
		String url = "http://www.baidu.com";
		try {
			HttpResponseWrapper wrapper = fetcher.sendHttpGet(url, null);
			assertNotNull(wrapper);
			System.out.println(wrapper.content);
			System.out.println(wrapper.realUrl);
			System.out.println(url);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void testHttpHead() throws ClientProtocolException, IOException {
		String url = "http://180.97.33.107";
		HttpResponseWrapper wrapper = fetcher.sendHttpHead(url, null);
		assertNotNull(wrapper);
		Header[]  headers = wrapper.getAllHeader();
		for (Header h : headers) {
			System.out.println(h.getName() + "\t" + h.getValue());
		}
	}
	public void tearDown() throws IOException{
		fetcher.close();
	}
}
