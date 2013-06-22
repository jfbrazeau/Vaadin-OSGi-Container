package org.vaadin.osgiappcontainer;

import java.io.IOException;
import java.net.URL;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.framework.Bundle;
import org.osgi.service.http.HttpContext;

public class OSGiUIHttpContext implements HttpContext {

	private HttpContext defaultContext;
	private Bundle uiBundle;
	
	protected OSGiUIHttpContext(HttpContext defaultContext, Bundle uiBundle) {
		this.defaultContext = defaultContext;
		this.uiBundle = uiBundle;
	}

	@Override
	public boolean handleSecurity(HttpServletRequest request,
			HttpServletResponse response) throws IOException {
		return defaultContext.handleSecurity(request, response);
	}
	
	@Override
	public URL getResource(String name) {
		URL url = defaultContext.getResource(name);
		return url == null ? uiBundle.getResource(name) : url;
	}
	
	@Override
	public String getMimeType(String name) {
		return defaultContext.getMimeType(name);
	}

}
