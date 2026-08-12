package fr.julm.keycloak.providers.workflow.sendverifyemail;

import jakarta.mail.internet.MimeMessage;

import org.keycloak.representations.userprofile.config.UPConfig;
import org.keycloak.representations.userprofile.config.UPConfig.UnmanagedAttributePolicy;
import org.keycloak.testframework.realm.UserBuilder;

import org.junit.jupiter.api.Test;

import fr.julm.keycloak.providers.workflow.sendverifyemail.support.AbstractSendVerifyEmailWorkflowTest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests the step's custom-message mode (the {@code message} config), including variable substitution in
 * {@code message}, {@code subject} and the default/custom subject resolution, mirroring the behaviour documented
 * in the extension's README.
 *
 * <p>Abstract on purpose: it has no {@code @KeycloakIntegrationTest} of its own and cannot be instantiated, so it
 * is never picked up as a standalone test class. It only supplies {@code @Test} methods to the
 * {@code @Nested SendVerifyEmailStepIT.CustomMessage} subclass, which carries the annotation.
 */
public abstract class SendVerifyEmailStepCustomMessageScenario extends AbstractSendVerifyEmailWorkflowTest {

    @Test
    public void substitutesLinkAndUserVariablesInMessage() {
        allowUnmanagedUserAttributes();

        createOnUserCreatedWorkflow(sendVerifyEmailStep()
                .withConfig("message", "<p>Hello ${user.firstName} ${user.lastName}, click <a href=\"${link}\">here</a>. Team: ${user.department}</p>")
                .build());

        realm.admin().users().create(UserBuilder.create()
                .username("bob")
                .email("bob@example.com")
                .name("Bob", "Doe")
                .attribute("department", "Engineering")
                .build()).close();

        MimeMessage message = findEmailByRecipient("bob@example.com");
        assertNotNull(message);

        String body = content(message);
        assertThat(body, containsString("Hello Bob Doe"));
        assertThat(body, containsString("Team: Engineering"));
        assertThat(body, containsString("/login-actions/action-token"));
        assertNotNull(extractActionTokenJwt(message));
    }

    @Test
    public void substitutesRealmVariablesInMessage() {
        realm.updateWithCleanup(builder -> builder.displayName("My Test Realm"));

        createOnUserCreatedWorkflow(sendVerifyEmailStep()
                .withConfig("message", "Realm: ${realm.name} / ${realm.displayName} / ${realm.frontendUrl} / ${realmFullBaseUrl}")
                .build());

        realm.admin().users().create(UserBuilder.create()
                .username("carol")
                .email("carol@example.com")
                .build()).close();

        MimeMessage message = findEmailByRecipient("carol@example.com");
        assertNotNull(message);

        String body = content(message);
        assertThat(body, containsString("Realm: " + realm.getName()));
        assertThat(body, containsString("My Test Realm"));
        assertThat(body, containsString(FRONTEND_URL));
        assertThat(body, containsString("/realms/" + realm.getName()));
    }

    @Test
    public void usesDefaultAccountNotificationSubjectWhenNotConfigured() {
        createOnUserCreatedWorkflow(sendVerifyEmailStep()
                .withConfig("message", "Please verify your account.")
                .build());

        realm.admin().users().create(UserBuilder.create()
                .username("dave")
                .email("dave@example.com")
                .build()).close();

        MimeMessage message = findEmailByRecipient("dave@example.com");
        assertNotNull(message);
        assertEquals("Account Notification", getSubject(message));
    }

    @Test
    public void usesCustomSubjectWithVariableSubstitution() {
        createOnUserCreatedWorkflow(sendVerifyEmailStep()
                .withConfig("message", "Please verify your account.")
                .withConfig("subject", "Welcome ${user.firstName} to ${realm.name}")
                .build());

        realm.admin().users().create(UserBuilder.create()
                .username("erin")
                .email("erin@example.com")
                .firstName("Erin")
                .build()).close();

        MimeMessage message = findEmailByRecipient("erin@example.com");
        assertNotNull(message);
        assertEquals("Welcome Erin to " + realm.getName(), getSubject(message));
    }

    @Test
    public void blankMessageFallsBackToDefaultVerifyEmail() {
        createOnUserCreatedWorkflow(sendVerifyEmailStep()
                .withConfig("message", "   ")
                .build());

        realm.admin().users().create(UserBuilder.create()
                .username("frank")
                .email("frank@example.com")
                .build()).close();

        MimeMessage message = findEmailByRecipient("frank@example.com");
        assertNotNull(message);
        assertEquals("Verify email", getSubject(message));
    }

    /**
     * By default, the realm's declarative user profile silently drops attributes that are not part of its schema.
     * The "department" attribute used to test {@code ${user.department}} substitution needs it relaxed.
     */
    private void allowUnmanagedUserAttributes() {
        UPConfig config = realm.admin().users().userProfile().getConfiguration();
        config.setUnmanagedAttributePolicy(UnmanagedAttributePolicy.ENABLED);
        realm.admin().users().userProfile().update(config);
    }
}
