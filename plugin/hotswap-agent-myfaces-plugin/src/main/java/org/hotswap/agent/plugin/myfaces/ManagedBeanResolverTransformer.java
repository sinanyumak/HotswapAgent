/**
 * 
 */
package org.hotswap.agent.plugin.myfaces;

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
public class ManagedBeanResolverTransformer {

	private static AgentLogger LOGGER = AgentLogger.getLogger(ManagedBeanResolverTransformer.class);

	public static final String DIRTY_BEANS_FIELD = "DIRTY_BEANS";

	
    @OnClassLoadEvent(classNameRegexp = "org.apache.myfaces.el.unified.resolver.ManagedBeanResolver")
    public static void init(CtClass ctClass, ClassLoader classLoader) throws CannotCompileException, NotFoundException {
    	LOGGER.info("Patching managed bean resolver. Class loader: {}", classLoader);
        
    	initClassPool(ctClass);

    	createDirtyBeansField(ctClass);
    	createAddToDirtyBeansMethod(ctClass);
    	createGetManagedBeanInfosMethod(ctClass);
    	createUpdateRuntimeConfigMethod(ctClass);
    	createCreateDirtyManagedBeansMethod(ctClass);
    	createProcessDirtyBeansMethod(ctClass);
    	
    	patchGetValueMethod(ctClass, classLoader);
    	
    	LOGGER.info("Patched managed bean resolver.");
    }

    private static void initClassPool(CtClass ctClass) {
    	ClassPool classPool = ctClass.getClassPool();

    	classPool.importPackage("java.lang");
    	classPool.importPackage("java.util");
    	classPool.importPackage("java.util.logging");
    	classPool.importPackage("org.apache.myfaces.config.annotation");
    	classPool.importPackage("org.apache.myfaces.config.impl.digester.elements");
    	classPool.importPackage("org.hotswap.agent.util");
    	classPool.importPackage("javax.faces.context");
    	classPool.importPackage("org.apache.myfaces.config");
    	classPool.importPackage("org.apache.myfaces.config.element");
    }

    private static void createDirtyBeansField(CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtField dirtyBeansField = CtField.make(
            "public static List " + DIRTY_BEANS_FIELD + " = new ArrayList();" , ctClass
        );
        ctClass.addField(dirtyBeansField);
    }

    private static void createAddToDirtyBeansMethod(CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtMethod addToDirtyBeansMethod = CtMethod.make(
	        "public static synchronized void addToDirtyBeans(Class beanClass) {" +
    			"log.log(Level.FINEST, \"Adding to dirty beans. Class: \" + beanClass);" +

				DIRTY_BEANS_FIELD + ".add(beanClass);" +

    			"log.log(Level.FINEST, \"Added to dirty beans.\");" +
			"}",
	        ctClass
        );

        ctClass.addMethod(addToDirtyBeansMethod);
    }
    
    private static void createGetManagedBeanInfosMethod(CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtMethod getManagedBeanInfosMethod = CtMethod.make(
	        "public FacesConfig getManagedBeanInfos() {" +
	        	"log.log(Level.FINEST, \"Getting managed bean infos.\");" +
    				
	        	"FacesConfig facesConfig = new FacesConfig(); " +
	        	"Set dirtyBeansSet = new HashSet(" + DIRTY_BEANS_FIELD + "); "+

				"AnnotationConfigurator ac = new AnnotationConfigurator(); " + 
				"ReflectionHelper.invoke(ac, " + 
						"AnnotationConfigurator.class, " + 
						"\"handleManagedBean\", " + 
						"new Class[] {FacesConfig.class, Set.class}, " + 
						"new Object[] {facesConfig, dirtyBeansSet} " + 
				"); " +

	        	"log.log(Level.FINEST, \"Getting managed bean infos.\");" +
	        	"return facesConfig;" +
			"}",
	        ctClass
        );

        ctClass.addMethod(getManagedBeanInfosMethod);
    }

    private static void createUpdateRuntimeConfigMethod(CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtMethod updateRuntimeConfigMethod = CtMethod.make(
	        "public void updateRuntimeConfig(FacesConfig facesConfig) {" +
    			"log.log(Level.FINEST, \"Updating Runtime Config managed bean definitions.\");" +
    			
    			"ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext(); " +
    			"RuntimeConfig runtimeConfig = RuntimeConfig.getCurrentInstance(externalContext); " +
    			
    			"List managedBeans = facesConfig.getManagedBeans(); " +

    			"Iterator iterator = managedBeans.iterator(); "+
    			"while (iterator.hasNext()) {" +
    				
    				"ManagedBean dirtyBean = (ManagedBean)iterator.next(); " +
    				"runtimeConfig.addManagedBean(dirtyBean.getManagedBeanName(), dirtyBean); " +

    				"iterator.remove();" +

				"} "+

    			"log.log(Level.FINEST, \"Updated Runtime Config managed bean definitions.\");" +
			"}",
	        ctClass
        );

        ctClass.addMethod(updateRuntimeConfigMethod);
    }

    private static void createCreateDirtyManagedBeansMethod(CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtMethod createDirtyManagedBeansMethod = CtMethod.make(
	        "public void createDirtyManagedBeans(FacesConfig facesConfig) {" +
    			"log.log(Level.FINEST, \"Creating dirty managed beans.\");" +
    			
    			"FacesContext facesContext = FacesContext.getCurrentInstance(); " +
    			
    			"List managedBeans = facesConfig.getManagedBeans(); " +

    			"Iterator iterator = managedBeans.iterator(); "+
    			"while (iterator.hasNext()) {" +
    				
    				"ManagedBean dirtyBean = (ManagedBean)iterator.next(); " +
    				"this.createManagedBean(dirtyBean, facesContext); " +

    				"iterator.remove();" +

    				"log.log(Level.INFO, \"Reloaded managed bean. Bean name: \" + dirtyBean.getManagedBeanName());" +
				"} "+

    			"log.log(Level.FINEST, \"Created dirty managed beans.\");" +
			"}",
	        ctClass
        );

        ctClass.addMethod(createDirtyManagedBeansMethod);
    }

    private static void createProcessDirtyBeansMethod(CtClass ctClass) throws CannotCompileException, NotFoundException {
        CtMethod processDirtyBeansMethod = CtMethod.make(
	        "public synchronized void processDirtyBeans() {" +
	        	"log.log(Level.FINEST, \"Processing dirty beans.\");" +

	        	"if ("+ DIRTY_BEANS_FIELD + ".isEmpty()) { " +
        			"log.log(Level.WARNING, \"No dirty bean found. Returning.\");" +
	        		"return; " +
	        	"} " +

	        	"FacesContext facesContext = FacesContext.getCurrentInstance(); " +
	        	"if (facesContext == null) { "+ 
	        		"log.log(Level.WARNING, \"Faces context is null. Returning.\");" +
    				"return;" +
    			"}" +

	        	"FacesConfig facesConfig = getManagedBeanInfos(); " +

	        	"this.updateRuntimeConfig(facesConfig); " +
        		"this.createDirtyManagedBeans(facesConfig); " +
	        	
				DIRTY_BEANS_FIELD + ".clear(); " +
				
    			"log.log(Level.FINEST, \"Processed dirty beans.\");" +
			"}",
	        ctClass
        );

        ctClass.addMethod(processDirtyBeansMethod);
    }

    private static void patchGetValueMethod(CtClass ctClass, ClassLoader classLoader) throws CannotCompileException, NotFoundException {
    	ClassPool classPool = ctClass.getClassPool();
    	
    	CtMethod getValueMethod = ctClass.getDeclaredMethod("getValue", new CtClass[] {
            classPool.get("javax.el.ELContext"),
            classPool.get("java.lang.Object"),
            classPool.get("java.lang.Object")
    	});

        getValueMethod.insertAfter("processDirtyBeans();");
    }

}
