/**
 * 
 */
package org.hotswap.agent.plugin.mojarra;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;


/**
 * @author sinan.yumak
 *
 */
public class BeanManagerTransformer {

	private static AgentLogger LOGGER = AgentLogger.getLogger(BeanManagerTransformer.class);

	public static final String DIRTY_BEANS_FIELD = "DIRTY_BEANS";
	
	
    @OnClassLoadEvent(classNameRegexp = "com.sun.faces.mgbean.BeanManager")
    public static void patchBeanManager(ClassPool classPool, CtClass ctClass) throws CannotCompileException, NotFoundException {
    	LOGGER.info("Patching bean manager.");
    	
    	createDirtyBeansField(ctClass);

    	createAddToDirtyBeansMethod(ctClass);
    	createRegisterDirtyBeanMethod(ctClass);
    	createProcessDirtyBeansMethod(ctClass);
    	
    	patchGetBeanFromScopeMethods(classPool, ctClass);

    	LOGGER.info("Patched bean manager successfully.");
    }

	private static void createDirtyBeansField(CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtField dirtyBeansField = CtField.make(
            "public static java.util.List " + DIRTY_BEANS_FIELD + " = new java.util.ArrayList();" , ctClass
        );
        ctClass.addField(dirtyBeansField);
    }

    private static void createAddToDirtyBeansMethod(CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtMethod addToDirtyBeansMethod = CtMethod.make(
	        "public void addToDirtyBeans(com.sun.faces.mgbean.ManagedBeanInfo beanInfo) {" +
				DIRTY_BEANS_FIELD + ".add(beanInfo.getName());" +
	        "}",
	        ctClass
        );

        ctClass.addMethod(addToDirtyBeansMethod);
    }

    private static void createRegisterDirtyBeanMethod(CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtMethod registerDirtyBeanMethod = CtMethod.make(
	        "public void registerDirtyBean(com.sun.faces.mgbean.ManagedBeanInfo beanInfo) {" +
	            "this.register(beanInfo);" +
				"this.addToDirtyBeans(beanInfo);" +
	        "}",
	        ctClass
        );

        ctClass.addMethod(registerDirtyBeanMethod);
    }

    private static void createProcessDirtyBeansMethod(CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtMethod processDirtyBeansMethod = CtMethod.make(
	        "public void processDirtyBeans(javax.faces.context.FacesContext facesContext) {" +

    			"java.util.Iterator iterator = " + DIRTY_BEANS_FIELD + ".iterator(); "+
    			"while (iterator.hasNext()) {" +
    				"java.lang.String dirtyBean = (java.lang.String)iterator.next(); " +
	        		"this.create((java.lang.String)dirtyBean, facesContext); " +
    			"} "+
	        "}",
	        ctClass
        );

        ctClass.addMethod(processDirtyBeansMethod);
    }

    private static void patchGetBeanFromScopeMethods(ClassPool classPool, CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtMethod getBeanFromScopeMethod1 = ctClass.getDeclaredMethod("getBeanFromScope", new CtClass[] {
            classPool.get("java.lang.String"),
            classPool.get("javax.faces.context.FacesContext"),
        });
        getBeanFromScopeMethod1.insertBefore("processDirtyBeans(context);");

        CtMethod getBeanFromScopeMethod2 = ctClass.getDeclaredMethod("getBeanFromScope", new CtClass[] {
            classPool.get("java.lang.String"),
            classPool.get("com.sun.faces.mgbean.BeanBuilder"),
            classPool.get("javax.faces.context.FacesContext"),
        });
        getBeanFromScopeMethod2.insertBefore("processDirtyBeans(context);");
	}

}
