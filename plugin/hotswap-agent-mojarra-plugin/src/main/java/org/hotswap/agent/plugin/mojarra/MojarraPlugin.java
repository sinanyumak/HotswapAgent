/*
 * Copyright 2013-2019 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.mojarra;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.OnResourceFileEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.plugin.mojarra.transformer.BeanManagerTransformer;
import org.hotswap.agent.plugin.mojarra.transformer.LifecycleImplTransformer;
import org.hotswap.agent.plugin.mojarra.transformer.ManagedBeanConfigHandlerTransformer;
import org.hotswap.agent.plugin.mojarra.transformer.MojarraTransformer;
import org.hotswap.agent.util.AnnotationHelper;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;

@Plugin(name = "Mojarra",
        description = "JSF/Mojarra. Clear resource bundle cache when *.properties files are changed.",
        testedVersions = {"2.1.23, 2.2.8"},
        expectedVersions = {"2.1", "2.2"},
        supportClass = { 
    		MojarraTransformer.class,
    		BeanManagerTransformer.class,
    		LifecycleImplTransformer.class,
    		ManagedBeanConfigHandlerTransformer.class
        }
)
public class MojarraPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(MojarraPlugin.class);

    private static final String MANAGED_BEAN_ANNOTATION = "javax.faces.bean.ManagedBean";
    
    
    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    Set<Object> registeredRBMaps = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());

    @Init
    public void init(PluginConfiguration pluginConfiguration) {
        LOGGER.info("Mojarra plugin initialized.");
    }

    @OnClassLoadEvent(classNameRegexp = "com.sun.faces.config.ConfigManager")
    public static void facesConfigManagerInitialized(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod init = ctClass.getDeclaredMethod("initialize");
        init.insertAfter(PluginManagerInvoker.buildInitializePlugin(MojarraPlugin.class));
        LOGGER.debug("com.sun.faces.config.ConfigManager enhanced with plugin initialization.");
    }

    @OnClassLoadEvent(classNameRegexp = "com.sun.faces.application.ApplicationResourceBundle")
    public static void facesApplicationAssociateInitialized(CtClass ctClass) throws NotFoundException, CannotCompileException {
        String registerResourceBundle = PluginManagerInvoker.buildCallPluginMethod(MojarraPlugin.class, "registerApplicationResourceBundle",
                "baseName", "java.lang.String", "resources", "java.lang.Object");
        String buildInitializePlugin = PluginManagerInvoker.buildInitializePlugin(MojarraPlugin.class);

        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter(buildInitializePlugin);
            constructor.insertAfter(registerResourceBundle);
        }
        LOGGER.debug("com.sun.faces.application.ApplicationAssociate enhanced with resource bundles registration.");
    }

    public void registerApplicationResourceBundle(String baseName, Object resourceBundle) {
        registeredRBMaps.add(resourceBundle);
        LOGGER.debug("JsfPlugin - resource bundle '" + baseName + "' registered");
    }

    @OnResourceFileEvent(path = "/", filter = ".*.properties")
    public void refreshJsfResourceBundles() {
        scheduler.scheduleCommand(refreshResourceBundles);
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void refreshManagedBeans(CtClass clazz, Class<?> original) {
    	try {
    		if (!AnnotationHelper.hasAnnotation(clazz, MANAGED_BEAN_ANNOTATION)) {
        		return;
    		}
    		LOGGER.info("Reloading managed bean: {}", clazz.getName());
    		
    		Object applicationAssociate = getApplicationAssociate();
    		LOGGER.info("Found Associate: {}", applicationAssociate.toString());
    		
    		Object beanManager = ReflectionHelper.get(applicationAssociate, "beanManager");
    		LOGGER.info("Bean Manager: {}", beanManager.toString());

    		Class resolveClass = resolveClass("com.sun.faces.application.annotation.ManagedBeanConfigHandler");
    		Object managedBeanConfigHandler = resolveClass.newInstance();
    		
    		Class managedBeanAnnotation = resolveClass("javax.faces.bean.ManagedBean");
    		ReflectionHelper.invoke(
    				managedBeanConfigHandler,
    				resolveClass,
    				"processDirtyBeans",
    				new Class[] {beanManager.getClass(), Class.class, Annotation.class},
    				beanManager,
    				original,
    				original.getAnnotation(managedBeanAnnotation)
    		);

    	} catch (Exception ex) {
    		LOGGER.info(ex.getMessage(), ex);
		}
    	    	
    }

    private Object getApplicationAssociate() {
    	ClassLoader oldContextClassLoader = Thread.currentThread().getContextClassLoader();

    	try {
    		Thread.currentThread().setContextClassLoader(appClassLoader);

    		Class<?> factoryFinderClass = resolveClass("javax.faces.FactoryFinder");
    		Method getFactoryMethod = factoryFinderClass.getDeclaredMethod("getFactory", String.class);
    		Object applicationFactory = getFactoryMethod.invoke(null, "javax.faces.application.ApplicationFactory");

    		Object application = ReflectionHelper.get(applicationFactory, "application");
    		Object applicationAssociate = ReflectionHelper.get(application, "associate");

    		return applicationAssociate;
    	} catch (Exception ex) {
    		LOGGER.info("Unable to get application associate: {}", ex.getMessage(), ex);
    	} finally {
    		Thread.currentThread().setContextClassLoader(oldContextClassLoader);
    	}
    	
    	return null;
    }

    private Command refreshResourceBundles = new Command() {
        public void executeCommand() {
            LOGGER.debug("Refreshing JSF resource bundles.");
            try {
                Class<?> clazz = resolveClass("java.util.ResourceBundle");
                Method clearCacheMethod = clazz.getDeclaredMethod("clearCache", ClassLoader.class);
                clearCacheMethod.invoke(null, appClassLoader);
                for (Object resourceMap : registeredRBMaps) {
                    if (resourceMap instanceof Map) {
                        ((Map) resourceMap).clear();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error clear JSF resource bundles cache", e);
            }
        }
    };

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, appClassLoader);
    }

}
