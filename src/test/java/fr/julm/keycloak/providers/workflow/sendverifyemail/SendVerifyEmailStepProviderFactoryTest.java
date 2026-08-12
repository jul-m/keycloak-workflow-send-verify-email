package fr.julm.keycloak.providers.workflow.sendverifyemail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.keycloak.models.workflow.ResourceType;
import org.keycloak.provider.ProviderConfigProperty;

class SendVerifyEmailStepProviderFactoryTest {

    private final SendVerifyEmailStepProviderFactory factory = new SendVerifyEmailStepProviderFactory();

    @Test
    void exposesTheWorkflowStepMetadata() {
        assertEquals("send-verify-email", factory.getId());
        assertEquals(Set.of(ResourceType.USERS), factory.getSupportedResourceTypes());
    }

    @Test
    void exposesOptionalConfigurationProperties() {
        Map<String, ProviderConfigProperty> properties = factory.getConfigProperties().stream()
                .collect(Collectors.toMap(property -> property.getName(), Function.identity()));

        assertTrue(properties.keySet().containsAll(Set.of(
            "message", "subject", "client_id", "redirect_uri", "lifespan", "reset_email_verification")));
        assertEquals(ProviderConfigProperty.BOOLEAN_TYPE,
            properties.get("reset_email_verification").getType());
    }
}