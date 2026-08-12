package fr.julm.keycloak.providers.workflow.sendverifyemail;

import java.util.Map;

import jakarta.mail.internet.MimeMessage;

import org.keycloak.testframework.realm.UserBuilder;

import org.junit.jupiter.api.Test;

import fr.julm.keycloak.providers.workflow.sendverifyemail.support.AbstractSendVerifyEmailWorkflowTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the step's default behaviour: without a {@code message} config it must delegate to
 * {@code EmailTemplateProvider.sendVerifyEmail}, preserving the native verify-email subject, template and lifespan
 * resolution used by the Admin REST API's {@code send-verify-email} action.
 *
 * <p>Abstract on purpose: it has no {@code @KeycloakIntegrationTest} of its own and cannot be instantiated, so it
 * is never picked up as a standalone test class. It only supplies {@code @Test} methods to the
 * {@code @Nested SendVerifyEmailStepIT.DefaultMode} subclass, which carries the annotation.
 */
public abstract class SendVerifyEmailStepDefaultModeScenario extends AbstractSendVerifyEmailWorkflowTest {

    @Test
    public void sendsDefaultVerifyEmailOnUserCreated() {
        createOnUserCreatedWorkflow(sendVerifyEmailStep().build());

        realm.admin().users().create(UserBuilder.create()
                .username("alice")
                .email("alice@example.com")
                .name("Alice", "Wonderland")
                .build()).close();

        MimeMessage message = findEmailByRecipient("alice@example.com");
        assertNotNull(message, "Expected a verify-email message to be sent to alice@example.com");
        assertThat(getSubject(message), containsString("Verify email"));
        assertThat(content(message), containsString("/login-actions/action-token"));
        assertNotNull(extractActionTokenJwt(message), "The email should contain a signed action-token link");
    }

    @Test
    public void doesNotSendEmailWhenNoWorkflowIsConfigured() {
        realm.admin().users().create(UserBuilder.create()
                .username("bob")
                .email("bob@example.com")
                .build()).close();

        assertNull(findEmailByRecipient("bob@example.com"));
    }

    @Test
    public void defaultLifespanMatchesRealmActionTokenSetting() throws Exception {
        realm.updateWithCleanup(builder -> builder.update(rep -> rep.setActionTokenGeneratedByAdminLifespan(3600)));

        createOnUserCreatedWorkflow(sendVerifyEmailStep().build());

        realm.admin().users().create(UserBuilder.create()
                .username("carol")
                .email("carol@example.com")
                .build()).close();

        MimeMessage message = findEmailByRecipient("carol@example.com");
        assertNotNull(message);

        Map<String, Object> claims = decodeJwtPayload(extractActionTokenJwt(message));
        long exp = ((Number) claims.get("exp")).longValue();
        double expectedExp = (System.currentTimeMillis() / 1000.0) + 3600;
        assertThat((double) exp, closeTo(expectedExp, 30));
    }

    @Test
    public void lifespanConfigOverridesRealmDefault() throws Exception {
        realm.updateWithCleanup(builder -> builder.update(rep -> rep.setActionTokenGeneratedByAdminLifespan(3600)));

        createOnUserCreatedWorkflow(sendVerifyEmailStep()
                .withConfig("lifespan", "120")
                .build());

        realm.admin().users().create(UserBuilder.create()
                .username("dave")
                .email("dave@example.com")
                .build()).close();

        MimeMessage message = findEmailByRecipient("dave@example.com");
        assertNotNull(message);

        Map<String, Object> claims = decodeJwtPayload(extractActionTokenJwt(message));
        long exp = ((Number) claims.get("exp")).longValue();
        double expectedExp = (System.currentTimeMillis() / 1000.0) + 120;
        assertThat((double) exp, closeTo(expectedExp, 30));
    }
}
