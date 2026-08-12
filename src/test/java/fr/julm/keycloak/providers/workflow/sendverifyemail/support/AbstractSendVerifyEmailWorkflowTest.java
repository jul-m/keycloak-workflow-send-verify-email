package fr.julm.keycloak.providers.workflow.sendverifyemail.support;

import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import jakarta.ws.rs.core.Response;

import org.keycloak.common.util.Time;
import org.keycloak.models.workflow.WorkflowProvider;
import org.keycloak.representations.workflows.WorkflowRepresentation;
import org.keycloak.representations.workflows.WorkflowStepRepresentation;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.mail.MailServer;
import org.keycloak.testframework.mail.annotations.InjectMailServer;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmBuilder;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.remote.providers.runonserver.RunOnServer;
import org.keycloak.testframework.remote.runonserver.InjectRunOnServer;
import org.keycloak.testframework.remote.runonserver.RunOnServerClient;
import org.keycloak.testframework.util.ApiUtil;
import org.keycloak.util.JsonSerialization;

import fr.julm.keycloak.providers.workflow.sendverifyemail.SendVerifyEmailStepProviderFactory;

/**
 * Base class for the {@code send-verify-email} workflow step integration tests. It boots a Keycloak server with the
 * step's classes deployed (see {@link SendVerifyEmailWorkflowServerConfig}), a fresh realm per test method with a
 * stable {@code frontendUrl} attribute, and a mail server to capture emails sent by the step.
 */
public abstract class AbstractSendVerifyEmailWorkflowTest {

    protected static final String USER_CREATED_EVENT = "user-created";
    protected static final String FRONTEND_URL = "https://kc-test-frontend.local";

    private static final Pattern ACTION_TOKEN_KEY_PATTERN = Pattern.compile("[?&]key=([^\"'&\\s<]+)");

    @InjectRealm(lifecycle = LifeCycle.METHOD, config = TestRealmConfig.class)
    protected ManagedRealm realm;

    @InjectMailServer
    protected MailServer mailServer;

    @InjectRunOnServer(permittedPackages = "fr.julm.keycloak.providers.workflow.sendverifyemail")
    protected RunOnServerClient runOnServer;

    protected static WorkflowStepRepresentation.Builder sendVerifyEmailStep() {
        return WorkflowStepRepresentation.create().of(SendVerifyEmailStepProviderFactory.ID);
    }

    /**
     * Creates a workflow triggered by the {@code user-created} event with the given steps.
     */
    protected String createOnUserCreatedWorkflow(WorkflowStepRepresentation... steps) {
        return createWorkflow(WorkflowRepresentation.withName("myworkflow")
                .onEvent(USER_CREATED_EVENT)
                .withSteps(steps)
                .build());
    }

    protected String createWorkflow(WorkflowRepresentation representation) {
        try (Response response = realm.admin().workflows().create(representation)) {
            return ApiUtil.getCreatedId(response);
        }
    }

    /**
     * Simulates the workflow scheduler running after the given offset has elapsed, then resets the time offset.
     */
    protected void runScheduledSteps(Duration offset) {
        runOnServer.run((RunOnServer) session -> {
            WorkflowProvider provider = session.getProvider(WorkflowProvider.class);
            try {
                Time.setOffset(Math.toIntExact(offset.toSeconds()));
                provider.runScheduledSteps();
            } finally {
                Time.setOffset(0);
            }
        });
    }

    protected MimeMessage findEmailByRecipient(String email) {
        return Arrays.stream(mailServer.getReceivedMessages())
                .filter(message -> hasRecipient(message, email))
                .findFirst()
                .orElse(null);
    }

    protected boolean hasRecipient(MimeMessage message, String email) {
        try {
            return message.getAllRecipients() != null && Arrays.stream(message.getAllRecipients())
                    .anyMatch(recipient -> recipient.toString().equalsIgnoreCase(email));
        } catch (Exception e) {
            return false;
        }
    }

    protected static String getSubject(MimeMessage message) {
        try {
            return message.getSubject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static String content(MimeMessage message) {
        try {
            return content(message.getContent());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String content(Object content) throws Exception {
        if (content instanceof String string) {
            return string;
        }
        if (content instanceof Multipart multipart) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                if (part.isMimeType("text/*")) {
                    builder.append(content(part.getContent()));
                }
            }
            return builder.toString();
        }
        return String.valueOf(content);
    }

    /**
     * Extracts the signed action-token JWT (the {@code key} query parameter) from the verify-email link contained
     * in the email body.
     */
    protected static String extractActionTokenJwt(MimeMessage message) {
        Matcher matcher = ACTION_TOKEN_KEY_PATTERN.matcher(content(message));
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Decodes the payload of a JWT without verifying its signature, only used to inspect claims such as {@code exp}
     * in tests.
     */
    @SuppressWarnings("unchecked")
    protected static Map<String, Object> decodeJwtPayload(String jwt) throws Exception {
        String[] parts = jwt.split("\\.");
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        return JsonSerialization.readValue(payload, Map.class);
    }

    public static class TestRealmConfig implements RealmConfig {
        @Override
        public RealmBuilder configure(RealmBuilder realm) {
            return realm
                    .name("send-verify-email-test")
                    .attribute("frontendUrl", FRONTEND_URL);
        }
    }
}
