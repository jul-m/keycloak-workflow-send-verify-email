package fr.julm.keycloak.providers.workflow.sendverifyemail;

import jakarta.ws.rs.core.Response;

import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.testframework.realm.UserBuilder;
import org.keycloak.testframework.util.ApiUtil;

import org.junit.jupiter.api.Test;

import fr.julm.keycloak.providers.workflow.sendverifyemail.support.AbstractSendVerifyEmailWorkflowTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@code reset_email_verification} config: when enabled it must reset {@code emailVerified} to
 * {@code false} and add the native {@code VERIFY_EMAIL} required action, exactly like the built-in behaviour it
 * mirrors.
 *
 * <p>Abstract on purpose: it has no {@code @KeycloakIntegrationTest} of its own and cannot be instantiated, so it
 * is never picked up as a standalone test class. It only supplies {@code @Test} methods to the
 * {@code @Nested SendVerifyEmailStepIT.ResetEmailVerification} subclass, which carries the annotation.
 */
public abstract class SendVerifyEmailStepResetEmailVerificationScenario extends AbstractSendVerifyEmailWorkflowTest {

    @Test
    public void resetsEmailVerificationWhenEnabled() {
        createOnUserCreatedWorkflow(sendVerifyEmailStep()
                .withConfig("reset_email_verification", "true")
                .build());

        String userId = createVerifiedUser("alice", "alice@example.com");

        assertNotNull(findEmailByRecipient("alice@example.com"));

        UserRepresentation user = realm.admin().users().get(userId).toRepresentation();
        assertFalse(user.isEmailVerified());
        assertThat(user.getRequiredActions(), hasItem("VERIFY_EMAIL"));
    }

    @Test
    public void doesNotResetEmailVerificationByDefault() {
        createOnUserCreatedWorkflow(sendVerifyEmailStep().build());

        String userId = createVerifiedUser("bob", "bob@example.com");

        assertNotNull(findEmailByRecipient("bob@example.com"));

        UserRepresentation user = realm.admin().users().get(userId).toRepresentation();
        assertTrue(user.isEmailVerified());
        assertThat(user.getRequiredActions(), not(hasItem("VERIFY_EMAIL")));
    }

    @Test
    public void doesNotResetEmailVerificationWhenExplicitlyDisabled() {
        createOnUserCreatedWorkflow(sendVerifyEmailStep()
                .withConfig("reset_email_verification", "false")
                .build());

        String userId = createVerifiedUser("carol", "carol@example.com");

        assertNotNull(findEmailByRecipient("carol@example.com"));

        UserRepresentation user = realm.admin().users().get(userId).toRepresentation();
        assertTrue(user.isEmailVerified());
        assertThat(user.getRequiredActions(), not(hasItem("VERIFY_EMAIL")));
    }

    private String createVerifiedUser(String username, String email) {
        try (Response response = realm.admin().users().create(UserBuilder.create()
                .username(username)
                .email(email)
                .emailVerified(true)
                .build())) {
            return ApiUtil.getCreatedId(response);
        }
    }
}
