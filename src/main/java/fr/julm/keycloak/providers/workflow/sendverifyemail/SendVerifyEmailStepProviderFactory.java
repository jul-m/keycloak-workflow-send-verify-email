package fr.julm.keycloak.providers.workflow.sendverifyemail;

import java.util.List;
import java.util.Set;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.workflow.ResourceType;
import org.keycloak.models.workflow.WorkflowStepProviderFactory;
import org.keycloak.provider.ProviderConfigProperty;

/**
 * Factory for the {@code send-verify-email} workflow step. Registered via
 * {@code META-INF/services/org.keycloak.models.workflow.WorkflowStepProviderFactory} and
 * discovered by Keycloak's SPI loader.
 */
public class SendVerifyEmailStepProviderFactory implements WorkflowStepProviderFactory<SendVerifyEmailStepProvider> {

    public static final String ID = "send-verify-email";

    @Override
    public SendVerifyEmailStepProvider create(KeycloakSession session, ComponentModel model) {
        return new SendVerifyEmailStepProvider(session, model);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public Set<ResourceType> getSupportedResourceTypes() {
        return Set.of(ResourceType.USERS);
    }

    @Override
    public String getHelpText() {
        return "Sends a verify-email action link to the workflow user";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of(
                property("message", "Message", "Optional custom message. Supports ${link} and user./realm. properties.", ProviderConfigProperty.STRING_TYPE),
                property("subject", "Subject", "Optional message key used as the email subject in custom-message mode.", ProviderConfigProperty.STRING_TYPE),
                property("client_id", "Client ID", "Optional client ID used to validate the redirect URI and set the action-token client.", ProviderConfigProperty.STRING_TYPE),
                property("redirect_uri", "Redirect URI", "Optional redirect URI validated against the selected client.", ProviderConfigProperty.STRING_TYPE),
                property("lifespan", "Lifespan", "Optional action-token lifespan in seconds.", ProviderConfigProperty.STRING_TYPE),
                property("reset_email_verification", "Reset email verification", "If enabled, sets emailVerified to false and adds the VERIFY_EMAIL required action.", ProviderConfigProperty.BOOLEAN_TYPE));
    }

    private static ProviderConfigProperty property(String name, String label, String helpText, String type) {
        return new ProviderConfigProperty(name, label, helpText, type, null);
    }
}
