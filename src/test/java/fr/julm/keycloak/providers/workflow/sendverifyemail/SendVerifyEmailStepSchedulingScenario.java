package fr.julm.keycloak.providers.workflow.sendverifyemail;

import java.time.Duration;

import jakarta.ws.rs.core.Response;

import org.keycloak.models.workflow.ResourceType;
import org.keycloak.representations.workflows.WorkflowRepresentation;
import org.keycloak.testframework.realm.UserBuilder;
import org.keycloak.testframework.util.ApiUtil;

import org.junit.jupiter.api.Test;

import fr.julm.keycloak.providers.workflow.sendverifyemail.support.AbstractSendVerifyEmailWorkflowTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests that the step also works when scheduled with a delay ({@code after}), and when triggered ad-hoc (a
 * workflow with no event trigger, activated explicitly through the Admin API) rather than synchronously on the
 * {@code user-created} event.
 *
 * <p>Abstract on purpose: it has no {@code @KeycloakIntegrationTest} of its own and cannot be instantiated, so it
 * is never picked up as a standalone test class. It only supplies {@code @Test} methods to the
 * {@code @Nested SendVerifyEmailStepIT.Scheduling} subclass, which carries the annotation.
 */
public abstract class SendVerifyEmailStepSchedulingScenario extends AbstractSendVerifyEmailWorkflowTest {

    @Test
    public void delayedStepOnlyRunsOnceItIsDue() {
        createOnUserCreatedWorkflow(sendVerifyEmailStep()
                .after(Duration.ofDays(2))
                .build());

        realm.admin().users().create(UserBuilder.create()
                .username("alice")
                .email("alice@example.com")
                .build()).close();

        // not due yet, right after user creation
        assertNull(findEmailByRecipient("alice@example.com"));

        // still not due after 1 day
        runScheduledSteps(Duration.ofDays(1));
        assertNull(findEmailByRecipient("alice@example.com"));

        // due after 2 days
        runScheduledSteps(Duration.ofDays(2));
        assertNotNull(findEmailByRecipient("alice@example.com"));
    }

    @Test
    public void adHocActivationRunsTheStepWithoutAnEventTrigger() {
        String workflowId;
        try (Response response = realm.admin().workflows().create(WorkflowRepresentation.withName("adhoc")
                .withSteps(sendVerifyEmailStep().build())
                .build())) {
            workflowId = ApiUtil.getCreatedId(response);
        }

        String userId;
        try (Response response = realm.admin().users().create(UserBuilder.create()
                .username("bob")
                .email("bob@example.com")
                .build())) {
            userId = ApiUtil.getCreatedId(response);
        }

        // no "on" trigger configured, so creating the user above must not have triggered the workflow
        assertNull(findEmailByRecipient("bob@example.com"));

        realm.admin().workflows().workflow(workflowId).activate(ResourceType.USERS.name(), userId);
        runScheduledSteps(Duration.ZERO);

        assertNotNull(findEmailByRecipient("bob@example.com"));
    }
}
