/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jsf.deployment;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.ee.structure.DeploymentType;
import org.jboss.as.ee.structure.DeploymentTypeMarker;
import org.jboss.as.jsf.logging.JSFLogger;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.as.web.common.WarMetaData;
import org.jboss.metadata.javaee.spec.ParamValueMetaData;
import org.jboss.metadata.web.jboss.JBossWebMetaData;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.filter.PathFilters;

/**
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 * @author Stuart Douglas
 */
public class JSFDependencyProcessor implements DeploymentUnitProcessor {
    public static final String IS_CDI_PARAM = "org.jboss.jbossfaces.IS_CDI";

    private static final String JSF_SUBSYSTEM = "org.jboss.as.jsf";
    // We use . instead of / on this stream as a workaround to get it transformed correctly by Batavia into a Jakarta namespace
    private static final String JAVAX_FACES_EVENT_NAMEDEVENT_class = "/jakarta.faces.event.NamedEvent".replaceAll("\\.", "/") + ".class";

    private JSFModuleIdFactory moduleIdFactory = JSFModuleIdFactory.getInstance();

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final DeploymentUnit tl = deploymentUnit.getParent() == null ? deploymentUnit : deploymentUnit.getParent();
        final ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
        final ModuleLoader moduleLoader = Module.getBootModuleLoader();
        //Set default when no default version has been set on the war file
        String jsfVersion = JsfVersionMarker.getVersion(tl).equals(JsfVersionMarker.NONE)? JSFModuleIdFactory.getInstance().getDefaultSlot() : JsfVersionMarker.getVersion(tl);
        String defaultJsfVersion = JSFModuleIdFactory.getInstance().getDefaultSlot();

        if(JsfVersionMarker.isJsfDisabled(deploymentUnit)) {
            if (jsfVersion.equals(defaultJsfVersion) && !moduleIdFactory.isValidJSFSlot(jsfVersion)) {
                throw JSFLogger.ROOT_LOGGER.invalidDefaultJSFImpl(defaultJsfVersion);
            }
            addJSFAPI(JsfVersionMarker.JSF_4_0, moduleSpecification, moduleLoader);
            return;
        }
        if (!DeploymentTypeMarker.isType(DeploymentType.WAR, deploymentUnit) && !DeploymentTypeMarker.isType(DeploymentType.EAR, deploymentUnit)) {
            return;
        }

        if (jsfVersion.equals(JsfVersionMarker.WAR_BUNDLES_JSF_IMPL)) {
            //if Jakarta Server Faces is provided by the application we leave it alone
            return;
        }
        //TODO: we should do that same check that is done in com.sun.faces.config.FacesInitializer
        //and only add the dependency if Jakarta Server Faces is actually needed


        if (!moduleIdFactory.isValidJSFSlot(jsfVersion)) {
            JSFLogger.ROOT_LOGGER.unknownJSFVersion(jsfVersion, defaultJsfVersion);
            jsfVersion = defaultJsfVersion;
        }

        if (jsfVersion.equals(defaultJsfVersion) && !moduleIdFactory.isValidJSFSlot(jsfVersion)) {
            throw JSFLogger.ROOT_LOGGER.invalidDefaultJSFImpl(defaultJsfVersion);
        }

        addJSFAPI(jsfVersion, moduleSpecification, moduleLoader);
        addJSFImpl(jsfVersion, moduleSpecification, moduleLoader);

        moduleSpecification.addSystemDependency(ModuleDependency.Builder.of(moduleLoader, JSF_SUBSYSTEM).setImportServices(true).build());

        addJSFInjection(jsfVersion, moduleSpecification, moduleLoader);

        WarMetaData warMetaData = deploymentUnit.getAttachment(WarMetaData.ATTACHMENT_KEY);
        if(warMetaData != null) {
            addCDIFlag(warMetaData, deploymentUnit);
        }
    }

    private void addJSFAPI(String jsfVersion, ModuleSpecification moduleSpecification, ModuleLoader moduleLoader) {
        if (jsfVersion.equals(JsfVersionMarker.WAR_BUNDLES_JSF_IMPL)) return;

        String jsfModule = moduleIdFactory.getApiModId(jsfVersion);
        ModuleDependency jsfAPI = ModuleDependency.Builder.of(moduleLoader, jsfModule).build();
        moduleSpecification.addSystemDependency(jsfAPI);
    }

    private void addJSFImpl(String jsfVersion, ModuleSpecification moduleSpecification, ModuleLoader moduleLoader) {
        if (jsfVersion.equals(JsfVersionMarker.WAR_BUNDLES_JSF_IMPL)) return;

        String jsfModule = moduleIdFactory.getImplModId(jsfVersion);
        ModuleDependency jsfImpl = ModuleDependency.Builder.of(moduleLoader, jsfModule).setImportServices(true).build();
        jsfImpl.addImportFilter(PathFilters.getMetaInfFilter(), true);
        moduleSpecification.addSystemDependency(jsfImpl);
    }

    private void addJSFInjection(String jsfVersion, ModuleSpecification moduleSpecification, ModuleLoader moduleLoader)
            throws DeploymentUnitProcessingException {
        if (jsfVersion.equals(JsfVersionMarker.WAR_BUNDLES_JSF_IMPL)) return;

        String jsfInjectionModule = moduleIdFactory.getInjectionModId(jsfVersion);
        ModuleDependency jsfInjectionDependency = ModuleDependency.Builder.of(moduleLoader, jsfInjectionModule).setExport(true).setImportServices(true).build();

        try {
            if (isJSF12(jsfInjectionDependency, jsfInjectionModule.toString())) {
                JSFLogger.ROOT_LOGGER.loadingJsf12();
                jsfInjectionDependency.addImportFilter(PathFilters.is("META-INF/faces-config.xml"), false);
                jsfInjectionDependency.addImportFilter(PathFilters.is("META-INF/1.2/faces-config.xml"), true);
            } else {
                JSFLogger.ROOT_LOGGER.loadingJsf2x();
                jsfInjectionDependency.addImportFilter(PathFilters.getMetaInfFilter(), true);
                // Exclude Faces 1.2 faces-config.xml to make extra sure it won't interfere with JSF 2.0 deployments
                jsfInjectionDependency.addImportFilter(PathFilters.is("META-INF/1.2/faces-config.xml"), false);
            }
        } catch (ModuleLoadException e) {
            throw JSFLogger.ROOT_LOGGER.jsfInjectionFailed(jsfVersion, e);
        }

        moduleSpecification.addSystemDependency(jsfInjectionDependency);
    }

    private boolean isJSF12(ModuleDependency moduleDependency, String identifier) throws ModuleLoadException {

        // The class jakarta.faces.event.NamedEvent was introduced in JSF 2.0
        return (moduleDependency.getModuleLoader().loadModule(identifier)
                .getClassLoader().getResource(JAVAX_FACES_EVENT_NAMEDEVENT_class) == null);
    }

    // Add a flag to the servlet context so that we know if we need to instantiate
    // a Jakarta Contexts and Dependency Injection ViewHandler.
    private void addCDIFlag(WarMetaData warMetaData, DeploymentUnit deploymentUnit) {
        JBossWebMetaData webMetaData = warMetaData.getMergedJBossWebMetaData();
        if (webMetaData == null) {
            webMetaData = new JBossWebMetaData();
            warMetaData.setMergedJBossWebMetaData(webMetaData);
        }

        List<ParamValueMetaData> contextParams = webMetaData.getContextParams();
        if (contextParams == null) {
            contextParams = new ArrayList<ParamValueMetaData>();
        }

        ParamValueMetaData param = new ParamValueMetaData();
        param.setParamName(IS_CDI_PARAM);
        param.setParamValue("true");
        contextParams.add(param);

        webMetaData.setContextParams(contextParams);
    }
}
