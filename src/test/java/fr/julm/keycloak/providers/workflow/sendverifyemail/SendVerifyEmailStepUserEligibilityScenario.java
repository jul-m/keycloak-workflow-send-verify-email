package fr.julm.keycloak.providers.workflow.sendverifyemail;

import org.keycloak.testframework.realm.UserBuilder;

import org.junit.jupiter.api.Test;

import fr.julm.keycloak.providers.workflow.sendverifyemail.support.AbstractSendVerifyEmailWorkflowTest;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The step delegates user eligibility checks to {@code UserResource.verifySendEmailParams}, which requires the
 * user to have an email address and to be enabled. Both checks throw a {@code WebApplicationException} that the
 * workflow engine catches and logs (see {@code DefaultWorkflowProvider#processEvent}), so from a caller's
 * perspective the step simply does not send an email and does not otherwise disrupt user creation.
 *
 * <p>Abstract on purpose: it has no {@code @KeycloakIntegrationTest} of its own and cannot be instantiated, so it
 * is never picked up as a standalone test class. It only supplies {@code @Test} methods to the
 * {@code @Nested SendVerifyEmailStepIT.UserEligibility} subclass, which carries the annotation.
 */
public abstract class SendVerifyEmailStepUserEligibilityScenario extends AbstractSendVerifyEmailWorkflowTest {

    @Test
    public void doesNotSendEmailToUserWithoutEmailAddress() {
        createOnUserCreatedWorkflow(sendVerifyEmailStep().build());

        // user creation itself must still succeed even though the step will fail internally
        realm.admin().users().create(UserBuilder.create()
                .username("no-email-user")
                .build()).close();

        assertNull(findEmailByRecipient("no-email-user"));
    }

    @Test
    public void doesNotSendEmailToDisabledUser() {
        createOnUserCreatedWorkflow(sendVerifyEmailStep().build());

        realm.admin().users().create(UserBuilder.create()
                .username("disabled-user")
                .email("disabled-user@example.com")
                .enabled(false)
                .build()).close();

        assertNull(findEmailByRecipient("disabled-user@example.com"));
    }
}
