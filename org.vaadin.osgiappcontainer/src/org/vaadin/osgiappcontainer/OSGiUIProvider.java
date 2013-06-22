package org.vaadin.osgiappcontainer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

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

	public static final String BUNDLES_TO_RELOAD_PARAM = "OSGiBundlesToReload";
	
	private static boolean first = true;

	@Override
	public synchronized Class<? extends UI> getUIClass(UIClassSelectionEvent event) {
System.err.println(Thread.currentThread().getId() + "getUIClass(" + event + ")");
		VaadinRequest request = event.getRequest();
		DeploymentConfiguration cfg = request.getService()
				.getDeploymentConfiguration();
		String uiBundleId = cfg.getApplicationOrSystemProperty(
				OSGiUIProvider.UI_BUNDLE_PARAM, null);
		Bundle uiBundle = Platform.getBundle(uiBundleId);
		
		try {
	        CurrentInstance.clearAll();
			if (!first && event.getRequest().getParameterMap().containsKey("restartApplication")) {
				System.err.println("** RELOAD REQUEST");
				// Build a list containing all bundles to reload
				List<Bundle> bundlesToReload = new ArrayList<Bundle>();
				if (uiBundle.getState() == Bundle.ACTIVE) {
					System.err.println("Must reload " + uiBundle.getSymbolicName());
					bundlesToReload.add(uiBundle);
				}
				String bundlesToReloadStr =  cfg.getApplicationOrSystemProperty(
						OSGiUIProvider.BUNDLES_TO_RELOAD_PARAM, null);
				if (bundlesToReloadStr != null && bundlesToReloadStr.length() > 0) {
					for (String bundleId : bundlesToReloadStr.trim().split(" ")) {
						Bundle bundle = Platform.getBundle(bundleId);
						if (bundle.getState() == Bundle.ACTIVE) {
							System.err.println("Must reload " + bundle.getSymbolicName());
							bundlesToReload.add(bundle);
						}
						else { 
							System.err.println("No need to restart " + bundle.getSymbolicName());
						}
					}
				}
	
//				for (Bundle bundle : bundlesToReload) {
//					System.err.println("Stopping " + bundle + " [" + Integer.toHexString(bundle.getState()) + "]");
//					bundle.stop();
//					System.err.println(Integer.toHexString(uiBundle.getState()));
//				}
//				for (Bundle bundle : bundlesToReload) {
				for (int i=bundlesToReload.size() - 1; i>=0; i--) {
					Bundle bundle = bundlesToReload.get(i);
					System.err.println("Starting " + bundle + " [" + Integer.toHexString(bundle.getState()) + "]");
					bundle.update();
					System.err.println(Integer.toHexString(uiBundle.getState()));
				}
			}
			
			String uiClassName = cfg.getApplicationOrSystemProperty(
					OSGiUIProvider.UI_CLASS_PARAM, null);
			first = false;
			return uiBundle.loadClass(uiClassName).asSubclass(UI.class);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Could not find UI class", e);
		} catch (BundleException e) {
			throw new RuntimeException("Could not reload UI bundle", e);
		}
		finally {
			System.err.println("stf=" + Integer.toHexString(uiBundle.getState()));

		}
	}

}
