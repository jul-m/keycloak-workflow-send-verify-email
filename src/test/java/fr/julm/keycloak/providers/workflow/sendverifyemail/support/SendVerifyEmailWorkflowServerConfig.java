package fr.julm.keycloak.providers.workflow.sendverifyemail.support;

import org.keycloak.common.Profile;
import org.keycloak.testframework.server.KeycloakServerConfig;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;

/**
 * Boots the test Keycloak server with the {@code workflows} feature enabled, the workflow executor running
 * synchronously (so assertions can run right after triggering an event, without polling), and this module's
 * compiled classes deployed as a provider so the {@code send-verify-email} step is registered.
 */
public class SendVerifyEmailWorkflowServerConfig implements KeycloakServerConfig {

    @Override
    public KeycloakServerConfigBuilder configure(KeycloakServerConfigBuilder config) {
        return config
                .features(Profile.Feature.WORKFLOWS)
                .option("spi-workflow--default--executor-blocking", Boolean.TRUE.toString())
                .dependencyCurrentProject();
    }
}
