package org.vaadin.osgiappcontainer;

import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;

import com.vaadin.server.Constants;
import com.vaadin.server.DeploymentConfiguration;
import com.vaadin.server.UIClassSelectionEvent;
import com.vaadin.server.UIProvider;
import com.vaadin.server.VaadinRequest;
import com.vaadin.ui.UI;
import com.vaadin.util.CurrentInstance;

@SuppressWarnings("serial")
public class OSGiUIProvider extends UIProvider implements Constants {

	public static final String UI_BUNDLE_PARAM = "OSGiUIBundle";

	public static final String UI_CLASS_PARAM = "OSGiUiClass";

	@Override
	public synchronized Class<? extends UI> getUIClass(UIClassSelectionEvent event) {
		VaadinRequest request = event.getRequest();
		DeploymentConfiguration cfg = request.getService()
				.getDeploymentConfiguration();
		String uiBundleId = cfg.getApplicationOrSystemProperty(
				OSGiUIProvider.UI_BUNDLE_PARAM, null);
		Bundle uiBundle = Platform.getBundle(uiBundleId);
		try {
	        CurrentInstance.clearAll();
			String uiClassName = cfg.getApplicationOrSystemProperty(
					OSGiUIProvider.UI_CLASS_PARAM, null);
			return uiBundle.loadClass(uiClassName).asSubclass(UI.class);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Could not find UI class", e);
		}
	}

}
