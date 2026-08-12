package fr.julm.keycloak.providers.workflow.sendverifyemail;

import static org.keycloak.common.util.StringPropertyReplacer.replaceProperties;

import java.util.HashMap;
import java.util.Map;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.logging.Logger;
import org.keycloak.authentication.actiontoken.verifyemail.VerifyEmailActionToken;
import org.keycloak.common.util.StringPropertyReplacer.PropertyResolver;
import org.keycloak.component.ComponentModel;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.models.KeycloakUriInfo;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.workflow.WorkflowExecutionContext;
import org.keycloak.models.workflow.WorkflowStepProvider;
import org.keycloak.services.Urls;
import org.keycloak.services.resources.LoginActionsService;
import org.keycloak.services.resources.admin.UserResource;
import org.keycloak.urls.UrlType;

/**
 * Workflow step that sends a signed email-verification action link to the workflow user.
 *
 * <p>Two mutually exclusive send modes are supported, selected by the presence of the
 * {@code message} config property:
 * <ul>
 *   <li>Without {@code message}: delegates to {@link EmailTemplateProvider#sendVerifyEmail},
 *       reusing the same template and behavior as the native Admin API {@code send-verify-email}.</li>
 *   <li>With {@code message}: renders the workflow notification template ({@code notify-user}
 *       style), after substituting {@code ${link}}, {@code ${user.*}} and {@code ${realm.*}}
 *       placeholders in both the message and the subject.</li>
 * </ul>
 *
 * <p>Validation of the user/client/redirect-uri and default values are delegated to
 * {@link UserResource#verifySendEmailParams} so behavior stays aligned with the native API.
 */
public class SendVerifyEmailStepProvider implements WorkflowStepProvider {

    private static final Logger LOG = Logger.getLogger(SendVerifyEmailStepProvider.class);
    private static final String CUSTOM_MESSAGE_TEMPLATE = "workflow-notification.ftl";
    private static final String DEFAULT_SUBJECT = "accountNotificationSubject";

    private final KeycloakSession session;
    private final ComponentModel stepModel;

    public SendVerifyEmailStepProvider(KeycloakSession session, ComponentModel stepModel) {
        this.session = session;
        this.stepModel = stepModel;
    }

    @Override
    public void run(WorkflowExecutionContext context) {
        RealmModel realm = session.getContext().getRealm();
        UserModel user = session.users().getUserById(realm, context.getResourceId());

        if (user == null) {
            LOG.warnv("Cannot send verify email: workflow user {0} was not found", context.getResourceId());
            return;
        }

        UriInfo uriInfo = getUriInfo(realm);
        MessagePropertyResolver propertyResolver = new MessagePropertyResolver(
            context, realm, null, uriInfo.getBaseUri());
        String redirectUri = resolveConfigValue("redirect_uri", propertyResolver);
        String clientId = config("client_id");
        Integer lifespan = parseLifespan();
        UserResource.SendEmailParams params = UserResource.verifySendEmailParams(
                session, realm, user, redirectUri, clientId, lifespan);

        if (shouldResetEmailVerification()) {
            user.setEmailVerified(false);
            user.addRequiredAction(UserModel.RequiredAction.VERIFY_EMAIL);
            LOG.debugv("Reset email verification for user {0}", user.getId());
        }

        String link = createActionTokenLink(realm, user, params, uriInfo);
        String message = config("message");

        try {
            EmailTemplateProvider emailProvider = session.getProvider(EmailTemplateProvider.class)
                    .setRealm(realm)
                    .setUser(user);

            if (message == null || message.trim().isEmpty()) {
                emailProvider.sendVerifyEmail(link, TimeUnit.SECONDS.toMinutes(params.getLifespan()));
            } else {
                propertyResolver = new MessagePropertyResolver(context, realm, link, uriInfo.getBaseUri());
                String subject = replaceProperties(
                    configOrDefault("subject", DEFAULT_SUBJECT), propertyResolver);
                emailProvider.send(
                    subject,
                        CUSTOM_MESSAGE_TEMPLATE,
                    getBodyAttributes(message, subject, propertyResolver));
            }

            LOG.debugv("Verify email sent to user {0} ({1})", user.getUsername(), user.getEmail());
        } catch (EmailException e) {
            LOG.errorv(e, "Failed to send verify email to user {0} ({1})", user.getUsername(), user.getEmail());
        }
    }

    /**
     * Workflows can run outside of an HTTP request (e.g. on a scheduled trigger), in which case
     * there is no active {@link UriInfo} to build links from. In that case, fall back to the
     * realm's configured {@code frontendUrl} instead of failing silently with a relative/broken link.
     */
    private UriInfo getUriInfo(RealmModel realm) {
        try {
            return session.getContext().getUri();
        } catch (ContextNotActiveException e) {
            String frontendUrl = realm.getAttribute("frontendUrl");
            if (frontendUrl == null || frontendUrl.isBlank()) {
                throw new IllegalStateException(
                        "Cannot generate a verify-email link from an asynchronous workflow: "
                                + "configure the realm frontendUrl", e);
            }

            return new KeycloakUriInfo(session, UrlType.FRONTEND, null);
        }
    }

    private String createActionTokenLink(RealmModel realm, UserModel user,
                                         UserResource.SendEmailParams params, UriInfo uriInfo) {
        int expiration = Math.toIntExact(Instant.now().getEpochSecond()) + params.getLifespan();
        VerifyEmailActionToken token = new VerifyEmailActionToken(
                user.getId(), expiration, null, user.getEmail(), params.getClientId());
        token.setRedirectUri(params.getRedirectUri());

        return buildActionTokenLink(token, realm, uriInfo);
    }

    private String buildActionTokenLink(VerifyEmailActionToken token, RealmModel realm, UriInfo uriInfo) {
        URI baseUri = uriInfo.getBaseUri();
        return Urls.loginActionsBase(baseUri)
            .path(LoginActionsService.class, "executeActionToken")
            .queryParam(Constants.KEY, token.serialize(session, realm, uriInfo))
                .build(realm.getName())
                .toString();
    }

    private Map<String, Object> getBodyAttributes(String message, String subject,
                                                   MessagePropertyResolver propertyResolver) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("messageKey", "customMessage");
        attributes.put("customMessage", replaceProperties(message, propertyResolver));
        attributes.put("daysRemaining", 0);
        attributes.put("reason", "verify-email");
        attributes.put("realmName", propertyResolver.resolve("realm.displayName") != null
                ? propertyResolver.resolve("realm.displayName")
                : propertyResolver.resolve("realm.name"));
        attributes.put("subjectKey", subject);
        attributes.put("link", propertyResolver.resolve("link"));
        return attributes;
    }

    private Integer parseLifespan() {
        String value = config("lifespan");
        return value == null || value.trim().isEmpty() ? null : Integer.valueOf(value);
    }

    private boolean shouldResetEmailVerification() {
        return Boolean.parseBoolean(config("reset_email_verification"));
    }

    private String config(String key) {
        return stepModel.getConfig().getFirst(key);
    }

    private String resolveConfigValue(String key, MessagePropertyResolver propertyResolver) {
        String value = config(key);
        return value == null ? null : replaceProperties(value, propertyResolver);
    }

    private String configOrDefault(String key, String defaultValue) {
        String value = config(key);
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }

    @Override
    public void close() {
    }

    /**
     * Resolves the {@code ${link}}, {@code ${user.*}}, {@code ${realm.*}} and
     * {@code ${realmFullBaseUrl}} placeholders shared by {@code message}, {@code subject} and
     * {@code redirect_uri}. {@code link} is {@code null} before the action-token link is
     * generated, since {@code redirect_uri} is resolved (and validated) before the link exists.
     */
    private final class MessagePropertyResolver implements PropertyResolver {
        private final WorkflowExecutionContext context;
        private final RealmModel realm;
        private final String link;
        private final URI baseUri;

        private MessagePropertyResolver(WorkflowExecutionContext context, RealmModel realm,
                                        String link, URI baseUri) {
            this.context = context;
            this.realm = realm;
            this.link = link;
            this.baseUri = baseUri;
        }

        @Override
        public String resolve(String property) {
            if ("link".equals(property)) {
                return link;
            }
            if ("realm.frontendUrl".equals(property)) {
                return realm.getAttribute("frontendUrl");
            }
            if ("realm.baseUrl".equals(property)) {
                return Urls.realmBase(baseUri).build(realm.getName()).toString();
            }
            if ("realmFullBaseUrl".equals(property)) {
                return Urls.realmIssuer(baseUri, realm.getName());
            }
            if (property.startsWith("user.")) {
                UserModel user = session.users().getUserById(realm, context.getResourceId());
                return user == null ? null : user.getFirstAttribute(property.substring("user.".length()));
            }
            if (property.startsWith("realm.")) {
                return switch (property.substring("realm.".length())) {
                    case "name" -> realm.getName();
                    case "displayName" -> realm.getDisplayName();
                    default -> null;
                };
            }
            return null;
        }
    }
}
