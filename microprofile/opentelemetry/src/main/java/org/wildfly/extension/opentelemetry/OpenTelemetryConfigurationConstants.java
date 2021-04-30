package org.wildfly.extension.opentelemetry;

public final class OpenTelemetryConfigurationConstants {
    private OpenTelemetryConfigurationConstants() {
    }

    public static final String OPENTELEMETRY_SERVICE_NAME = "opentelemetry.serviceName";
    public static final String OPENTELEMETRY_EXPORTER = "opentelemetry.exporter";



    public static final String OPENTELEMETRY_TRACER = "opentelemetry.tracer";

    public static final String TRACER_CONFIGURATION = "tracer-configuration";
    public static final String TRACER_CONFIGURATION_NAME = "tracer-configuration-name";

    public static final String EXPORTER = "exporter";
    public static final String SENDER_ENDPOINT = "sender-endpoint";

    /*
    public static final String DEFAULT_TRACER = "default-tracer";
    public static final String PROPAGATION = "propagation";

    public static final String SAMPLER_TYPE = "sampler-type";
    public static final String SAMPLER_PARAM = "sampler-param";
    public static final String SAMPLER_MANAGER_HOST_PORT = "sampler-manager-host-port";

    public static final String SENDER_AGENT_BINDING = "sender-binding";
    public static final String SENDER_AUTH_TOKEN = "sender-auth-token";
    public static final String SENDER_AUTH_USER = "sender-auth-user";
    public static final String SENDER_AUTH_PASSWORD = "sender-auth-password";

    public static final String REPORTER_LOG_SPANS = "reporter-log-spans";
    public static final String REPORTER_FLUSH_INTERVAL = "reporter-flush-interval";
    public static final String REPORTER_MAX_QUEUE_SIZE = "reporter-max-queue-size";

    public static final String TRACEID_128BIT = "tracer_id_128bit";
    public static final String TRACER_TAGS = "tags";
    */
}
