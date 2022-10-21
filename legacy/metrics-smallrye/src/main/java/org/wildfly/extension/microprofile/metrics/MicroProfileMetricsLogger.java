package org.wildfly.extension.microprofile.metrics;

import static org.wildfly.extension.microprofile.metrics.MicroProfileMetricsExtension.EXTENSION_NAME;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

@MessageLogger(projectCode = "WFLYMPMETRICS", length = 4)
interface MicroProfileMetricsLogger extends BasicLogger {
    MicroProfileMetricsLogger LOGGER = Logger.getMessageLogger(MicroProfileMetricsLogger.class,
            EXTENSION_NAME);

    @Message(id = 1, value = "The migrate operation can not be performed: the server must be in admin-only mode")
    OperationFailedException migrateOperationAllowedOnlyInAdminOnly();

    @Message(id = 2, value = "Migration failed, see results for more details.")
    String migrationFailed();
}
