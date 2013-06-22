package org.vaadin.osgiappcontainer;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IContributor;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.spi.RegistryContributor;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import com.vaadin.server.VaadinServlet;

// TODO clear warnings
@SuppressWarnings("rawtypes")
public class Activator implements BundleActivator, ServiceTrackerCustomizer {

	public static final String BUNDLE_ID = "org.vaadin.osgiappcontainer";

	private static final String UI_PROVIDER_PARAM = "UIProvider";

	private static final String UI_PARAM = "ui";
	
	private static final String WIDGETSET_PARAM = "widgetset";

	private static final String PRODUCTION_MODE_PARAM = "productionMode";

	private static final List<Class<?>> SERVICE_CLASSES = Arrays
			.asList(new Class<?>[] { IExtensionRegistry.class,
					HttpService.class /* , HttpContextExtensionService.class */});

	private static BundleContext context;

	public static BundleContext getContext() {
		return context;
	}

	private Map<Class<?>, ServiceTracker<?, ?>> serviceTrackers = new HashMap<Class<?>, ServiceTracker<?, ?>>();

	private boolean initialized = false;
	private HttpService httpService;
	private IExtensionRegistry extensionRegistryService;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext
	 * )
	 */
	@SuppressWarnings("unchecked")
	public void start(BundleContext bundleContext) throws Exception {
		Activator.context = bundleContext;
		for (Class<?> cl : SERVICE_CLASSES) {
			ServiceTracker st = new ServiceTracker(context, cl.getName(), this);
			serviceTrackers.put(cl, st);
			st.open();
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext bundleContext) throws Exception {
		Activator.context = null;
		for (ServiceTracker<?, ?> st : serviceTrackers.values()) {
			st.close();
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public Object addingService(ServiceReference reference) {
		Object service = context.getService(reference);
		if (service instanceof IExtensionRegistry
				&& extensionRegistryService == null)
			extensionRegistryService = (IExtensionRegistry) service;
		if (service instanceof HttpService && httpService == null) {
			httpService = (HttpService) service;
		}
		if (!initialized && extensionRegistryService != null
				&& httpService != null) {
			// Prevents from initializing several times
			initialized = true;
			init();
		}
		return null;
	}

	private void init() {
		IConfigurationElement[] cfgs = extensionRegistryService
				.getConfigurationElementsFor("org.vaadin.application");
		if (cfgs.length > 1) {
			logWarn("More than one vaadin application is configured. "
					+ "OSGi HttpService doesn't allow to define more "
					+ "than one vaadin instance. Only starting first "
					+ "occurence.");
		}
		if (cfgs.length > 0) {
			IConfigurationElement cfg = cfgs[0];
			Properties props = new Properties();
			props.put(PRODUCTION_MODE_PARAM,
					cfg.getAttribute(PRODUCTION_MODE_PARAM));
			String widgetset = cfg.getAttribute(WIDGETSET_PARAM);
			if (widgetset != null) {
				props.put(WIDGETSET_PARAM, widgetset);
			}
			
			// TODO constant
			props.put(UI_PROVIDER_PARAM, OSGiUIProvider.class.getName());
			final Bundle uiBundle = getBundle(cfg);
			props.setProperty(OSGiUIProvider.UI_BUNDLE_PARAM, uiBundle.getSymbolicName());
			props.setProperty(OSGiUIProvider.UI_CLASS_PARAM, cfg.getAttribute(UI_PARAM));
			StringBuilder sb = new StringBuilder();
			for (IConfigurationElement bundleToReloadCfg : cfg.getChildren("bundle-to-reload")) {
				String bundleId = bundleToReloadCfg.getAttribute("id");
				if (bundleId != null) {
					if (Platform.getBundle(bundleId) == null) {
						logWarn("Bundle '" + bundleId + "' is unknown => ignored.");
					}
					else {
						sb.append(bundleId);
						sb.append(' ');
					}
				}
			}
			if (sb.length() > 0) {
				props.setProperty(OSGiUIProvider.BUNDLES_TO_RELOAD_PARAM, sb.toString());
			}
			
			// Register application bundle
			Exception exception = null;
			try {
				httpService.registerServlet(
						"/",
						new VaadinServlet() {
							@Override
							public void service(ServletRequest req,
									ServletResponse res)
									throws ServletException, IOException {
								long start = System.currentTimeMillis();
								super.service(req, res);
System.out.println("Vaadin request process time : " + (System.currentTimeMillis() - start));
							}
						},
						props,
						new OSGiUIHttpContext(httpService
								.createDefaultHttpContext(), uiBundle));
			} catch (ServletException e) {
				exception = e;
			} catch (NamespaceException e) {
				exception = e;
			}
			if (exception != null) {
				logError("Error while registering Vaadin UI for '" + uiBundle.getBundleId()
						+ "'", exception);
			}
		}
	}

	private Bundle getBundle(IConfigurationElement cfg) {
		Bundle result = null;
		IContributor contributor = cfg.getContributor();
		if (contributor instanceof RegistryContributor) {
			long id = Long.parseLong(((RegistryContributor) contributor)
					.getActualId());
			Bundle thisBundle = FrameworkUtil.getBundle(getClass());
			result = thisBundle.getBundleContext().getBundle(id);
		} else {
			result = Platform.getBundle(contributor.getName());
		}
		return result;
	}

	@Override
	public void modifiedService(ServiceReference reference, Object service) {
	}

	@Override
	public void removedService(ServiceReference reference, Object service) {
		context.ungetService(reference);
	}

	public void logError(String message, Throwable exception) {
		ILog log = Platform.getLog(getContext().getBundle());
		log.log(new Status(IStatus.ERROR, BUNDLE_ID, message, exception));

	}

	public void logError(String message) {
		ILog log = Platform.getLog(getContext().getBundle());
		log.log(new Status(IStatus.ERROR, BUNDLE_ID, message));

	}

	public void logWarn(String message) {
		ILog log = Platform.getLog(getContext().getBundle());
		log.log(new Status(IStatus.WARNING, BUNDLE_ID, message));

	}

}
