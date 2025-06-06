/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.ejb3.deployment.processors.dd;

import static org.jboss.as.ejb3.logging.EjbLogger.ROOT_LOGGER;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.jboss.as.ee.component.Attachments;
import org.jboss.as.ee.component.ComponentDescription;
import org.jboss.as.ee.component.EEModuleDescription;
import org.jboss.as.ee.component.InterceptorDescription;
import org.jboss.as.ejb3.component.EJBComponentDescription;
import org.jboss.as.ejb3.deployment.EjbDeploymentAttachmentKeys;
import org.jboss.as.ejb3.logging.EjbLogger;
import org.jboss.as.ejb3.subsystem.EjbNameRegexService;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.reflect.ClassReflectionIndex;
import org.jboss.as.server.deployment.reflect.ClassReflectionIndexUtil;
import org.jboss.as.server.deployment.reflect.DeploymentReflectionIndex;
import org.jboss.invocation.proxy.MethodIdentifier;
import org.jboss.metadata.ejb.spec.EjbJarMetaData;
import org.jboss.metadata.ejb.spec.InterceptorBindingMetaData;
import org.jboss.metadata.ejb.spec.InterceptorMetaData;
import org.jboss.metadata.ejb.spec.NamedMethodMetaData;
import org.jboss.modules.Module;
/**
 * Processor that handles interceptor bindings that are defined in the deployment descriptor.
 *
 * @author Stuart Douglas
 */
public class DeploymentDescriptorInterceptorBindingsProcessor implements DeploymentUnitProcessor {

    private final EjbNameRegexService ejbNameRegexService;

    public DeploymentDescriptorInterceptorBindingsProcessor(EjbNameRegexService ejbNameRegexService) {
        this.ejbNameRegexService = ejbNameRegexService;
    }


    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {

        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final EjbJarMetaData metaData = deploymentUnit.getAttachment(EjbDeploymentAttachmentKeys.EJB_JAR_METADATA);
        final EEModuleDescription eeModuleDescription = deploymentUnit.getAttachment(Attachments.EE_MODULE_DESCRIPTION);
        final Module module = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.MODULE);
        final DeploymentReflectionIndex index = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.REFLECTION_INDEX);


        if (metaData == null) {
            return;
        }

        if (metaData.getAssemblyDescriptor() == null) {
            return;
        }
        if (metaData.getAssemblyDescriptor().getInterceptorBindings() == null) {
            return;
        }

        //default interceptors must be mentioned in the interceptors section
        final Set<String> interceptorClasses = new HashSet<String>();
        if (metaData.getInterceptors() != null) {
            for (final InterceptorMetaData interceptor : metaData.getInterceptors()) {
                interceptorClasses.add(interceptor.getInterceptorClass());
            }
        }

        final Map<String, List<InterceptorBindingMetaData>> bindingsPerComponent = new HashMap<String, List<InterceptorBindingMetaData>>();
        final List<InterceptorBindingMetaData> defaultInterceptorBindings = new ArrayList<InterceptorBindingMetaData>();

        for (final InterceptorBindingMetaData binding : metaData.getAssemblyDescriptor().getInterceptorBindings()) {
            if (binding.getEjbName().equals("*")) {
                if (binding.getMethod() != null) {
                    throw EjbLogger.ROOT_LOGGER.defaultInterceptorsNotBindToMethod();
                }
                if(binding.getInterceptorOrder() != null) {
                    throw EjbLogger.ROOT_LOGGER.defaultInterceptorsNotSpecifyOrder();
                }
                defaultInterceptorBindings.add(binding);
            } else if(ejbNameRegexService.isEjbNameRegexAllowed()) {
                Pattern pattern = Pattern.compile(binding.getEjbName());
                for (final ComponentDescription componentDescription : eeModuleDescription.getComponentDescriptions()) {
                    if(componentDescription instanceof EJBComponentDescription) {
                        String ejbName = ((EJBComponentDescription) componentDescription).getEJBName();
                        if(pattern.matcher(ejbName).matches()) {
                            List<InterceptorBindingMetaData> bindings = bindingsPerComponent.get(ejbName);
                            if (bindings == null) {
                                bindingsPerComponent.put(ejbName, bindings = new ArrayList<InterceptorBindingMetaData>());
                            }
                            bindings.add(binding);
                        }
                    }
                }
            } else {
                List<InterceptorBindingMetaData> bindings = bindingsPerComponent.get(binding.getEjbName());
                if (bindings == null) {
                    bindingsPerComponent.put(binding.getEjbName(), bindings = new ArrayList<InterceptorBindingMetaData>());
                }
                bindings.add(binding);
            }
        }


        final List<InterceptorDescription> defaultInterceptors = new ArrayList<InterceptorDescription>();

        for (InterceptorBindingMetaData binding : defaultInterceptorBindings) {
            if (binding.getInterceptorClasses() != null) {
                for (final String clazz : binding.getInterceptorClasses()) {
                    //we only want default interceptors referenced in the interceptors section
                    if (interceptorClasses.contains(clazz)) {
                        defaultInterceptors.add(new InterceptorDescription(clazz));
                    } else {
                        ROOT_LOGGER.defaultInterceptorClassNotListed(clazz);
                    }
                }
            }
        }

        //now we need to process the components, and add interceptor information
        //we iterate over all components, as we need to process default interceptors
        for (final ComponentDescription componentDescription : eeModuleDescription.getComponentDescriptions()) {

            final Class<?> componentClass;
            try {
                componentClass = module.getClassLoader().loadClass(componentDescription.getComponentClassName());
            } catch (ClassNotFoundException e) {
                throw EjbLogger.ROOT_LOGGER.failToLoadComponentClass(e, componentDescription.getComponentClassName());
            }

            final List<InterceptorBindingMetaData> bindings = bindingsPerComponent.get(componentDescription.getComponentName());
            final Map<Method, List<InterceptorBindingMetaData>> methodInterceptors = new HashMap<Method, List<InterceptorBindingMetaData>>();
            final List<InterceptorBindingMetaData> classLevelBindings = new ArrayList<InterceptorBindingMetaData>();
            //we only want to exclude default and class level interceptors if every binding
            //has the exclude element.
            boolean classLevelExcludeDefaultInterceptors = false;
            Map<Method, Boolean> methodLevelExcludeDefaultInterceptors = new HashMap<Method, Boolean>();
            Map<Method, Boolean> methodLevelExcludeClassInterceptors = new HashMap<Method, Boolean>();

            //if an absolute order has been defined at any level
            //absolute ordering takes precedence
            boolean classLevelAbsoluteOrder = false;
            final Map<Method, Boolean> methodLevelAbsoluteOrder = new HashMap<Method, Boolean>();


            if (bindings != null) {
                for (final InterceptorBindingMetaData binding : bindings) {
                    if (binding.getMethod() == null) {
                        classLevelBindings.add(binding);
                        //if even one binding does not say exclude default then we do not exclude
                        if (binding.isExcludeDefaultInterceptors()) {
                            classLevelExcludeDefaultInterceptors = true;
                        }
                        if (binding.isTotalOrdering()) {
                            if (classLevelAbsoluteOrder) {
                                throw EjbLogger.ROOT_LOGGER.twoEjbBindingsSpecifyAbsoluteOrder(componentClass.toString());
                            } else {
                                classLevelAbsoluteOrder = true;
                            }
                        }
                    } else {

                        //method level bindings
                        //first find the right method
                        final NamedMethodMetaData methodData = binding.getMethod();
                        final ClassReflectionIndex classIndex = index.getClassIndex(componentClass);
                        Method resolvedMethod = null;
                        if (methodData.getMethodParams() == null) {
                            final Collection<Method> methods = ClassReflectionIndexUtil.findAllMethodsByName(index, classIndex, methodData.getMethodName());
                            if (methods.isEmpty()) {
                                throw EjbLogger.ROOT_LOGGER.failToFindMethodInEjbJarXml(componentClass.getName(), methodData.getMethodName());
                            } else if (methods.size() > 1) {
                                throw EjbLogger.ROOT_LOGGER.multipleMethodReferencedInEjbJarXml(methodData.getMethodName(), componentClass.getName());
                            }
                            resolvedMethod = methods.iterator().next();
                        } else {
                            String[] params = methodData.getMethodParams().stream().map(this::mapArrayNotation).toArray(String[]::new);
                            final Collection<Method> methods = ClassReflectionIndexUtil.findMethods(index, classIndex, methodData.getMethodName(), params);
                            if (methods.isEmpty()) {
                                throw EjbLogger.ROOT_LOGGER.failToFindMethodInEjbJarXml(componentClass.getName(), methodData.getMethodName());
                            } else if (methods.size() > 1) {
                                throw EjbLogger.ROOT_LOGGER.multipleMethodReferencedInEjbJarXml(methodData.getMethodName(), componentClass.getName());
                            }
                            resolvedMethod = methods.iterator().next();
                        }
                        List<InterceptorBindingMetaData> list = methodInterceptors.get(resolvedMethod);
                        if (list == null) {
                            methodInterceptors.put(resolvedMethod, list = new ArrayList<InterceptorBindingMetaData>());
                        }
                        list.add(binding);
                        if (binding.isExcludeDefaultInterceptors()) {
                            methodLevelExcludeDefaultInterceptors.put(resolvedMethod, true);
                        }
                        if (binding.isExcludeClassInterceptors()) {
                            methodLevelExcludeClassInterceptors.put(resolvedMethod, true);
                        }

                        if (binding.isTotalOrdering()) {
                            if (methodLevelAbsoluteOrder.containsKey(resolvedMethod)) {
                                throw EjbLogger.ROOT_LOGGER.twoEjbBindingsSpecifyAbsoluteOrder(resolvedMethod.toString());
                            } else {
                                methodLevelAbsoluteOrder.put(resolvedMethod, true);
                            }
                        }

                    }
                }
            }

            //now we have all the bindings in a format we can use
            //build the list of default interceptors
            componentDescription.setDefaultInterceptors(defaultInterceptors);

            if(classLevelExcludeDefaultInterceptors) {
                componentDescription.setExcludeDefaultInterceptors(true);
            }

            final List<InterceptorDescription> classLevelInterceptors = new ArrayList<InterceptorDescription>();
            if (classLevelAbsoluteOrder) {
                //we have an absolute ordering for the class level interceptors
                for (final InterceptorBindingMetaData binding : classLevelBindings) {
                    if (binding.isTotalOrdering()) {
                        for (final String interceptor : binding.getInterceptorOrder()) {
                            classLevelInterceptors.add(new InterceptorDescription(interceptor));
                        }
                    }
                }
                //we have merged the default interceptors into the class interceptors
                componentDescription.setExcludeDefaultInterceptors(true);
            } else {
                //the order we want is default interceptors (this will be empty if they are excluded)
                //the annotation interceptors
                //then dd interceptors
                classLevelInterceptors.addAll(componentDescription.getClassInterceptors());
                for (InterceptorBindingMetaData binding : classLevelBindings) {
                    if (binding.getInterceptorClasses() != null) {
                        for (final String interceptor : binding.getInterceptorClasses()) {
                            classLevelInterceptors.add(new InterceptorDescription(interceptor));
                        }
                    }
                }
            }
            componentDescription.setClassInterceptors(classLevelInterceptors);

            for (Map.Entry<Method, List<InterceptorBindingMetaData>> entry : methodInterceptors.entrySet()) {
                final Method method = entry.getKey();
                final List<InterceptorBindingMetaData> methodBindings = entry.getValue();
                boolean totalOrder = methodLevelAbsoluteOrder.containsKey(method);
                final MethodIdentifier methodIdentifier = MethodIdentifier.getIdentifierForMethod(method);

                Boolean excludeDefaultInterceptors = methodLevelExcludeDefaultInterceptors.get(method);
                excludeDefaultInterceptors = excludeDefaultInterceptors == null ? Boolean.FALSE : excludeDefaultInterceptors;
                if (!excludeDefaultInterceptors) {
                    excludeDefaultInterceptors = componentDescription.isExcludeDefaultInterceptors() || componentDescription.isExcludeDefaultInterceptors(methodIdentifier);
                }

                Boolean excludeClassInterceptors = methodLevelExcludeClassInterceptors.get(method);
                excludeClassInterceptors = excludeClassInterceptors == null ? Boolean.FALSE : excludeClassInterceptors;
                if (!excludeClassInterceptors) {
                    excludeClassInterceptors = componentDescription.isExcludeClassInterceptors(methodIdentifier);
                }

                final List<InterceptorDescription> methodLevelInterceptors = new ArrayList<InterceptorDescription>();

                if (totalOrder) {
                    //if there is a total order we just use it
                    for (final InterceptorBindingMetaData binding : methodBindings) {
                        if (binding.isTotalOrdering()) {
                            for (final String interceptor : binding.getInterceptorOrder()) {
                                methodLevelInterceptors.add(new InterceptorDescription(interceptor));
                            }
                        }
                    }
                } else {
                    //add class level and default interceptors, if not excluded
                    //class level interceptors also includes default interceptors
                    if (!excludeDefaultInterceptors) {
                        methodLevelInterceptors.addAll(defaultInterceptors);
                    }
                    if (!excludeClassInterceptors) {
                        for (InterceptorDescription interceptor : classLevelInterceptors) {
                            methodLevelInterceptors.add(interceptor);
                        }
                    }
                    List<InterceptorDescription> annotationMethodLevel = componentDescription.getMethodInterceptors().get(methodIdentifier);
                    if (annotationMethodLevel != null) {
                        methodLevelInterceptors.addAll(annotationMethodLevel);
                    }
                    //now add all the interceptors from the bindings
                    for (InterceptorBindingMetaData binding : methodBindings) {
                        if (binding.getInterceptorClasses() != null) {
                            for (final String interceptor : binding.getInterceptorClasses()) {
                                methodLevelInterceptors.add(new InterceptorDescription(interceptor));
                            }
                        }
                    }

                }
                //we have already taken component and default interceptors into account
                componentDescription.excludeClassInterceptors(methodIdentifier);
                componentDescription.excludeDefaultInterceptors(methodIdentifier);
                componentDescription.setMethodInterceptors(methodIdentifier, methodLevelInterceptors);

            }

        }
    }

    private static final Map<String, String> primitiveArrayLiterals = new HashMap<>();

    //WFLY-20432
    static {
        primitiveArrayLiterals.put("java.lang.boolean", "[Z");
        primitiveArrayLiterals.put("java.lang.byte", "[B");
        primitiveArrayLiterals.put("java.lang.char", "[C");
        primitiveArrayLiterals.put("java.lang.double", "[D");
        primitiveArrayLiterals.put("java.lang.float", "[F");
        primitiveArrayLiterals.put("java.lang.int", "[I");
        primitiveArrayLiterals.put("java.lang.short", "[S");
        primitiveArrayLiterals.put("java.lang.long", "[J");
    }

    private String mapArrayNotation(String param) {
        if (param.endsWith("[]")) {
            String type = param.substring(0, param.length() - 2);
            if (primitiveArrayLiterals.containsKey(type)) {
                return primitiveArrayLiterals.get(type);
            } else if (primitiveArrayLiterals.containsKey("java.lang." + type)) {
                return primitiveArrayLiterals.get("java.lang." + type);
            } else {
                return "[L" + type + ";";
            }
        } else {
            return param;
        }
    }
}
