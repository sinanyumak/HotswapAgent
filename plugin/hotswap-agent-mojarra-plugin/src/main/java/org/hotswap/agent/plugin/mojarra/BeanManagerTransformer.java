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
	
	public static CtClass MODIFIED_BEAN_MANAGER;
	

	@OnClassLoadEvent(classNameRegexp = "com.sun.faces.mgbean.BeanManager")
    public static void patchBeanManager(ClassPool classPool, CtClass ctClass, ClassLoader classLoader) throws CannotCompileException, NotFoundException {
    	LOGGER.info("Patching bean manager. Class loader: {}", classLoader);
    	createDirtyBeansField(ctClass);

    	createAddToDirtyBeansMethod(ctClass);
    	createRegisterDirtyBeanMethod(ctClass);
    	createProcessDirtyBeansMethod(ctClass);
    	
    	LOGGER.info("Patched bean manager successfully.");
    	MODIFIED_BEAN_MANAGER = ctClass;
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
    			"LOGGER.log(java.util.logging.Level.WARNING, \"Adding to dirty beans.\");" +

				DIRTY_BEANS_FIELD + ".add(beanInfo.getName());" +

    			"LOGGER.log(java.util.logging.Level.WARNING, \"Added to dirty beans.\");" +
			"}",
	        ctClass
        );

        ctClass.addMethod(addToDirtyBeansMethod);
    }

    private static void createRegisterDirtyBeanMethod(CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtMethod registerDirtyBeanMethod = CtMethod.make(
	        "public void registerDirtyBean(com.sun.faces.mgbean.ManagedBeanInfo beanInfo) { " +
	            "this.register(beanInfo); " +
				"this.addToDirtyBeans(beanInfo); " +
	        "}",
	        ctClass
        );

        ctClass.addMethod(registerDirtyBeanMethod);
    }

    private static void createProcessDirtyBeansMethod(CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtMethod processDirtyBeansMethod = CtMethod.make(
	        "public synchronized void processDirtyBeans(javax.faces.context.FacesContext facesContext) {" +
	        	"LOGGER.log(java.util.logging.Level.WARNING, \"Processing dirty beans.\");" +
    			"if (facesContext == null) { "+ 
    				"return;" +
    			"}" +
    				
	        	"java.util.Iterator iterator = " + DIRTY_BEANS_FIELD + ".iterator(); "+
    			"while (iterator.hasNext()) {" +
    				
    				"java.lang.String dirtyBean = (java.lang.String)iterator.next(); " +

    				"com.sun.faces.mgbean.BeanBuilder beanBuilder = getBuilder(dirtyBean);" +
    				"this.preProcessBean(dirtyBean, beanBuilder); " +
    				
    				"this.create((java.lang.String)dirtyBean, facesContext); " +
    				"iterator.remove(dirtyBean);" +

				"} "+

    			"LOGGER.log(java.util.logging.Level.WARNING, \"Processed dirty beans.\");" +
			"}",
	        ctClass
        );

        ctClass.addMethod(processDirtyBeansMethod);
    }

}
