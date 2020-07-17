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
public class LifecycleImplTransformer {

	private static AgentLogger LOGGER = AgentLogger.getLogger(LifecycleImplTransformer.class);

	
    @OnClassLoadEvent(classNameRegexp = "com.sun.faces.lifecycle.LifecycleImpl")
    public static void patchConfigManager(CtClass ctClass, ClassLoader classLoader) throws CannotCompileException, NotFoundException {
    	LOGGER.info("Patching lifecycle implementation. classLoader: {}", classLoader);

    	initClassPool(ctClass);
    	patchAttachWindowMethod(ctClass, classLoader);

    	LOGGER.info("Patching lifecycle implementation successfully.");
    }

    private static void initClassPool(CtClass ctClass) {
    	ClassPool classPool = ctClass.getClassPool();
    	
    	BeanManagerTransformer.MODIFIED_BEAN_MANAGER.defrost();
    	classPool.makeClass(BeanManagerTransformer.MODIFIED_BEAN_MANAGER.getClassFile());

    	classPool.importPackage("com.sun.faces.application");
    	classPool.importPackage("com.sun.faces.mgbean");
    }
    
    private static void patchAttachWindowMethod(CtClass ctClass, ClassLoader classLoader) throws CannotCompileException, NotFoundException {
    	ClassPool classPool = ctClass.getClassPool();
    	
    	CtMethod renderMethod = ctClass.getDeclaredMethod("attachWindow", new CtClass[] {
            classPool.get("javax.faces.context.FacesContext"),
        });

        String processDirtyBeanCall = 
        	"ApplicationAssociate application = ApplicationAssociate.getCurrentInstance(); " +
        	"BeanManager beanManager = application.getBeanManager(); " +
        	"beanManager.processDirtyBeans(context); "
        	;
		
        renderMethod.insertAfter(processDirtyBeanCall);
    }

}
