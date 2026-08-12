package fr.julm.keycloak.providers.workflow.sendverifyemail;

import java.util.Map;

import jakarta.mail.internet.MimeMessage;
import jakarta.ws.rs.core.Response;

import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.testframework.realm.ClientBuilder;
import org.keycloak.testframework.realm.UserBuilder;
import org.keycloak.testframework.util.ApiUtil;

import org.junit.jupiter.api.Test;

import fr.julm.keycloak.providers.workflow.sendverifyemail.support.AbstractSendVerifyEmailWorkflowTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests {@code client_id} and {@code redirect_uri} handling, which the step delegates to
 * {@code UserResource.verifySendEmailParams} to stay aligned with the native Admin API. Validation failures there
 * (unknown client, disabled client, unregistered redirect URI) throw, which the workflow engine catches and logs -
 * so the observable behaviour from the test's point of view is simply "no email is sent".
 *
 * <p>Abstract on purpose: it has no {@code @KeycloakIntegrationTest} of its own and cannot be instantiated, so it
 * is never picked up as a standalone test class. It only supplies {@code @Test} methods to the
 * {@code @Nested SendVerifyEmailStepIT.ClientAndRedirectUri} subclass, which carries the annotation.
 */
public abstract class SendVerifyEmailStepClientAndRedirectUriScenario extends AbstractSendVerifyEmailWorkflowTest {

    @Test
    public void usesSystemClientWhenClientIdNotConfigured() {
        createOnUserCreatedWorkflow(sendVerifyEmailStep().build());

        realm.admin().users().create(UserBuilder.create()
                .username("alice")
                .email("alice@example.com")
                .build()).close();

        assertNotNull(findEmailByRecipient("alice@example.com"));
    }

    @Test
    public void redirectUriMatchingRegisteredClientUriIsIncludedInToken() throws Exception {
        createClient("test-client", "https://app.example.com/account*");

        createOnUserCreatedWorkflow(sendVerifyEmailStep()
                .withConfig("client_id", "test-client")
                .withConfig("redirect_uri", "https://app.example.com/account")
                .build());

        realm.admin().users().create(UserBuilder.create()
                .username("bob")
                .email("bob@example.com")
                .build()).close();

        MimeMessage message = findEmailByRecipient("bob@example.com");
        assertNotNull(message);

        // the redirect_uri and client_id are not part of the link itself, they are embedded (as "reduri"/"azp")
        // in the signed action-token carried by the "key" query parameter
        Map<String, Object> claims = decodeJwtPayload(extractActionTokenJwt(message));
        assertEquals("https://app.example.com/account", claims.get("reduri"));
        assertEquals("test-client", claims.get("azp"));
    }

    @Test
    public void unknownClientIdPreventsEmailFromBeingSent() {
        createOnUserCreatedWorkflow(sendVerifyEmailStep()
                .withConfig("client_id", "does-not-exist")
                .build());

        realm.admin().users().create(UserBuilder.create()
                .username("carol")
                .email("carol@example.com")
                .build()).close();

        assertNull(findEmailByRecipient("carol@example.com"));
    }

    @Test
    public void disabledClientPreventsEmailFromBeingSent() {
        createClient("disabled-client", false, "https://app.example.com/*");

        createOnUserCreatedWorkflow(sendVerifyEmailStep()
                .withConfig("client_id", "disabled-client")
                .build());

        realm.admin().users().create(UserBuilder.create()
                .username("dave")
                .email("dave@example.com")
                .build()).close();

        assertNull(findEmailByRecipient("dave@example.com"));
    }

    @Test
    public void redirectUriNotRegisteredOnClientPreventsEmailFromBeingSent() {
        createClient("strict-client", "https://app.example.com/allowed");

        createOnUserCreatedWorkflow(sendVerifyEmailStep()
                .withConfig("client_id", "strict-client")
                .withConfig("redirect_uri", "https://evil.example.com/phish")
                .build());

        realm.admin().users().create(UserBuilder.create()
                .username("erin")
                .email("erin@example.com")
                .build()).close();

        assertNull(findEmailByRecipient("erin@example.com"));
    }

    @Test
    public void redirectUriWithoutClientIdPreventsEmailFromBeingSent() {
        createOnUserCreatedWorkflow(sendVerifyEmailStep()
                .withConfig("redirect_uri", "https://app.example.com/account")
                .build());

        realm.admin().users().create(UserBuilder.create()
                .username("frank")
                .email("frank@example.com")
                .build()).close();

        assertNull(findEmailByRecipient("frank@example.com"));
    }

    private String createClient(String clientId, String... redirectUris) {
        return createClient(clientId, true, redirectUris);
    }

    private String createClient(String clientId, boolean enabled, String... redirectUris) {
        ClientRepresentation representation = ClientBuilder.create()
                .clientId(clientId)
                .enabled(enabled)
                .publicClient()
                .redirectUris(redirectUris)
                .build();
        try (Response response = realm.admin().clients().create(representation)) {
            return ApiUtil.getCreatedId(response);
        }
    }
}
