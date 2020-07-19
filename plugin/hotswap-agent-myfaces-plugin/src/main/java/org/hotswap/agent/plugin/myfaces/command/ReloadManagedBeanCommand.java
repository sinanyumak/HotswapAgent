/**
 * 
 */
package org.hotswap.agent.plugin.myfaces.command;

import static org.hotswap.agent.plugin.myfaces.MyFacesConstants.MANAGED_BEAN_RESOLVER_CLASS;

import org.hotswap.agent.command.Command;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.ReflectionHelper;

/**
 * A command to reload {@link javax.faces.bean.ManagedBean} classes.
 *
 * <p> It simply adds the bean class to dirty beans list. Bean class
 * will be reloaded on the next call to the servlet.
 *
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
