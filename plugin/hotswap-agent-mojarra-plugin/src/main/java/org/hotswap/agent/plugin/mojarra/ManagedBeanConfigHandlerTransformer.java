/**
 * 
 */
package org.hotswap.agent.plugin.mojarra;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;

/**
 * @author sinan.yumak
 *
 */
public class ManagedBeanConfigHandlerTransformer {

	private static AgentLogger LOGGER = AgentLogger.getLogger(ManagedBeanConfigHandlerTransformer.class);

	
    @OnClassLoadEvent(classNameRegexp = "com.sun.faces.application.annotation.ManagedBeanConfigHandler")
    public static void patchConfigManager(ClassPool classPool, CtClass ctClass, ClassLoader classLoader) throws CannotCompileException, NotFoundException {
    	LOGGER.info("Patching config handler. classLoader: {}", classLoader);

    	createProcessDirtyBeanMethod(ctClass);

    	LOGGER.info("Patched config handler successfully.");
    }

    private static void createProcessDirtyBeanMethod(CtClass ctClass) throws CannotCompileException, NotFoundException {        
    	BeanManagerTransformer.MODIFIED_BEAN_MANAGER.defrost();
    	ctClass.getClassPool().makeClass(BeanManagerTransformer.MODIFIED_BEAN_MANAGER.getClassFile());
    	
    	CtMethod processDirtyBeansMethod = CtMethod.make(
	        "public void processDirtyBeans(" + 
	        		"com.sun.faces.mgbean.BeanManager manager, " +
	        		"java.lang.Class annotatedClass, " + 
	        		"java.lang.annotation.Annotation annotation) {" +

				"com.sun.faces.mgbean.ManagedBeanInfo beanInfo = getBeanInfo(annotatedClass, (javax.faces.bean.ManagedBean) annotation);" +
            	"manager.registerDirtyBean(beanInfo);" +

            "}",
	        ctClass
        );

        ctClass.addMethod(processDirtyBeansMethod);
    }

}
