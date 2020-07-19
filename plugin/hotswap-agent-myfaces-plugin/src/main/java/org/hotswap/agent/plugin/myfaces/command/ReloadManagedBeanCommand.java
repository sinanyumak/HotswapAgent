/**
 * 
 */
package org.hotswap.agent.plugin.myfaces.command;

import static org.hotswap.agent.plugin.myfaces.MyFacesConstants.MANAGED_BEAN_ANNOTATION;
import static org.hotswap.agent.plugin.myfaces.MyFacesConstants.MANAGED_BEAN_RESOLVER_CLASS;

import org.hotswap.agent.command.Command;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.AnnotationHelper;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * @author sinan.yumak
 *
 */
public class ReloadManagedBeanCommand implements Command {

    private static AgentLogger LOGGER = AgentLogger.getLogger(ReloadManagedBeanCommand.class);

    private Class<?> beanClass;
    private ClassLoader classLoader;


	public ReloadManagedBeanCommand(Class<?> beanClass, ClassLoader classLoader) {
		this.beanClass = beanClass;
		this.classLoader = classLoader;
	}

	@Override
	public void executeCommand() {
		if (!AnnotationHelper.hasAnnotation(beanClass, MANAGED_BEAN_ANNOTATION)) {
    		return;
		}

		try {
			LOGGER.info("Reloading managed bean: {}", beanClass.getName());

    		Class<?> beanResolverClass = resolveClass(MANAGED_BEAN_RESOLVER_CLASS);
    		ReflectionHelper.invoke(
    				null,
    				beanResolverClass,
    				"addToDirtyBeans",
    				new Class[] {Class.class},
    				new Object[] {beanClass}
    		);

    	} catch (Exception ex) {
    		LOGGER.info("Unable to reload managed bean. Reason: {}", ex.getMessage(), ex);
		}
    	    	
    }

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, classLoader);
    }

}
