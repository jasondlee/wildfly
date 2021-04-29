package org.wildfly.extension.opentelemetry.deployment;

import static org.jboss.as.weld.Capabilities.WELD_CAPABILITY_NAME;
import static org.wildfly.extension.opentelemetry.OpenTelemetryConfigurationConstants.OPENTELEMETRY_SERVICE_NAME;
import static org.wildfly.extension.opentelemetry.OpenTelemetryConfigurationConstants.OPENTELEMETRY_TRACER;
import static org.wildfly.extension.opentelemetry.OpenTelemetryConfigurationConstants.TRACER_CONFIGURATION;
import static org.wildfly.extension.opentelemetry.OpenTelemetryConfigurationConstants.TRACER_CONFIGURATION_NAME;
import static org.wildfly.extension.opentelemetry.deployment.OpenTelemetryExtensionLogger.ROOT_LOGGER;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporterBuilder;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.ee.structure.DeploymentType;
import org.jboss.as.ee.structure.DeploymentTypeMarker;
import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentResourceSupport;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.web.common.ServletContextAttribute;
import org.jboss.as.web.common.WarMetaData;
import org.jboss.as.weld.WeldCapability;
import org.jboss.logging.Logger;
import org.jboss.metadata.javaee.spec.ParamValueMetaData;
import org.jboss.metadata.web.jboss.JBossWebMetaData;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.wildfly.extension.opentelemetry.extension.OpenTelemetrySubsystemExtension;
import org.wildfly.security.manager.WildFlySecurityManager;

public class OpenTelemetrySubsystemDeploymentProcessor implements DeploymentUnitProcessor {
    private static final AttachmentKey<OpenTelemetry> ATTACHMENT_KEY = AttachmentKey.create(OpenTelemetry.class);
    private static final AttachmentKey<Tracer> TRACER_ATTACHMENT_KEY = AttachmentKey.create(Tracer.class);

    public static final int PRIORITY = 0x4000;

    private Logger log = Logger.getLogger(OpenTelemetrySubsystemDeploymentProcessor.class);

    @Override
    public void deploy(DeploymentPhaseContext deploymentPhaseContext) throws DeploymentUnitProcessingException {
        ROOT_LOGGER.processingDeployment();
        final DeploymentUnit deploymentUnit = deploymentPhaseContext.getDeploymentUnit();
        if (DeploymentTypeMarker.isType(DeploymentType.EAR, deploymentUnit)) {
            return;
        }
        final CapabilityServiceSupport support = deploymentUnit.getAttachment(Attachments.CAPABILITY_SERVICE_SUPPORT);
        try {
            final WeldCapability weldCapability = support.getCapabilityRuntimeAPI(WELD_CAPABILITY_NAME, WeldCapability.class);
            if (!weldCapability.isPartOfWeldDeployment(deploymentUnit)) {
                // SmallRye Jakarta RESTful Web Services require Jakarta Contexts and Dependency Injection. Without Jakarta Contexts and Dependency Injection, there's no integration needed
                ROOT_LOGGER.noCdiDeployment();
                return;
            }
        } catch (CapabilityServiceSupport.NoSuchCapabilityException e) {
            //We should not be here since the subsystem depends on weld capability. Just in case ...
            throw new DeploymentUnitProcessingException(ROOT_LOGGER.deploymentRequiresCapability(
                    deploymentPhaseContext.getDeploymentUnit().getName(), WELD_CAPABILITY_NAME
            ));
        }
        injectTracer(deploymentPhaseContext, support);
    }

    @Override
    public void undeploy(DeploymentUnit context) {
    }

    // Basically a clone of TracingDeploymentProcessor.injectTracer(), but simplified until things are working, then
    // we'll add any missing complexity that's needed.
    private void injectTracer(DeploymentPhaseContext deploymentPhaseContext, CapabilityServiceSupport support) {
        final DeploymentUnit deploymentUnit = deploymentPhaseContext.getDeploymentUnit();
        final ClassLoader initialCl = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
        final Module module = deploymentUnit.getAttachment(Attachments.MODULE);
        final ModuleClassLoader moduleCL = module.getClassLoader();

        final JaegerGrpcSpanExporterBuilder jaeger = JaegerGrpcSpanExporter.builder();
//        jaeger.setEndpoint()
        final SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(jaeger.build()).build())
                .build();
        final OpenTelemetrySdkBuilder sdkBuilder = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()));

        String serviceName = getServiceName(deploymentUnit);

        final OpenTelemetry openTelemetry = sdkBuilder.build();
        final Tracer tracer = openTelemetry.getTracer(serviceName);
        OpenTelemetryCdiExtension.registerApplicationOpenTelemetryBean(moduleCL, openTelemetry);
        OpenTelemetryCdiExtension.registerApplicationTracer(moduleCL, tracer);

        deploymentUnit.putAttachment(ATTACHMENT_KEY, openTelemetry);
        deploymentUnit.addToAttachmentList(ServletContextAttribute.ATTACHMENT_KEY, new ServletContextAttribute(OPENTELEMETRY_SERVICE_NAME, serviceName));
        deploymentUnit.addToAttachmentList(ServletContextAttribute.ATTACHMENT_KEY, new ServletContextAttribute(OPENTELEMETRY_TRACER, tracer));
        ROOT_LOGGER.registeringTracer(tracer.getClass().getName());
        deploymentUnit.putAttachment(TRACER_ATTACHMENT_KEY, tracer);

        DeploymentResourceSupport deploymentResourceSupport = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_RESOURCE_SUPPORT);
        deploymentResourceSupport.getDeploymentSubsystemModel(OpenTelemetrySubsystemExtension.SUBSYSTEM_NAME).get(TRACER_CONFIGURATION).set(tracer.getClass().getName());
        deploymentResourceSupport.getDeploymentSubsystemModel(OpenTelemetrySubsystemExtension.SUBSYSTEM_NAME).get(TRACER_CONFIGURATION_NAME).set(tracer.getClass().getName());
    }

    private String getServiceName(DeploymentUnit deploymentUnit) {
        JBossWebMetaData jbossWebMetaData = getJBossWebMetaData(deploymentUnit);
        if (null == jbossWebMetaData) {
            // nothing to do here
            return "";
        }
        if (jbossWebMetaData.getContextParams() != null) {
            for (ParamValueMetaData param : jbossWebMetaData.getContextParams()) {
                if (OPENTELEMETRY_SERVICE_NAME.equals(param.getParamName())) {
                    return param.getParamValue();
                }
            }
        }
        String serviceName = WildFlySecurityManager.getPropertyPrivileged("JAEGER_SERVICE_NAME", "");
        if (null == serviceName || serviceName.isEmpty()) {
            serviceName = WildFlySecurityManager.getEnvPropertyPrivileged("JAEGER_SERVICE_NAME", "");
        }

        if (null == serviceName || serviceName.isEmpty()) {
            if (null != deploymentUnit.getParent()) {
                // application.ear!module.war
                serviceName = deploymentUnit.getParent().getServiceName().getSimpleName()
                        + "!"
                        + deploymentUnit.getServiceName().getSimpleName();
            } else {
                serviceName = deploymentUnit.getServiceName().getSimpleName();
            }

            ROOT_LOGGER.serviceNameDerivedFromDeploymentUnit(serviceName);
        }
        return serviceName;
    }

    private JBossWebMetaData getJBossWebMetaData(DeploymentUnit deploymentUnit) {
        WarMetaData warMetaData = deploymentUnit.getAttachment(WarMetaData.ATTACHMENT_KEY);
        if (null == warMetaData) {
            // not a web deployment, nothing to do here...
            return null;
        }
        return warMetaData.getMergedJBossWebMetaData();
    }
}
