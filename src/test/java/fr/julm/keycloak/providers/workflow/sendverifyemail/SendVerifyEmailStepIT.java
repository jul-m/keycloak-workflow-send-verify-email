package fr.julm.keycloak.providers.workflow.sendverifyemail;

import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import fr.julm.keycloak.providers.workflow.sendverifyemail.support.SendVerifyEmailWorkflowServerConfig;

/**
 * Integration tests for the {@code send-verify-email} workflow step. The actual scenarios live in separate
 * {@code SendVerifyEmailStep*Scenario} classes (one per file, for readability); this class glues each of them into
 * a {@link Nested} subclass carrying the {@code @KeycloakIntegrationTest} annotation, since a nested class's
 * {@code Class.getAnnotations()} doesn't see annotations declared on the enclosing class - each subclass needs its
 * own. Because every subclass shares the same {@code KeycloakServerConfig}, running/debugging this file as a whole
 * from the IDE Testing panel launches a single JVM, so the {@code KeycloakServer} test-framework instance
 * (LifeCycle.GLOBAL) boots once and is shared by every scenario below, instead of once per class as happens when
 * running/debugging separate top-level {@code *IT} classes individually.
 *
 * <p>Each {@code @Test} method below is a one-line {@code @Override} that calls {@code super.xxx()}: VS Code's
 * Testing panel (vscjava.vscode-java-test) only lists {@code @Test} methods declared directly in the class body it
 * scans - it does not resolve methods inherited from a superclass declared in another file. Redeclaring each method
 * here (instead of leaving the nested class body empty) keeps every scenario's actual logic in its own file while
 * still making each test individually visible/runnable in the Testing tree.
 */
public class SendVerifyEmailStepIT {

    @Nested
    @KeycloakIntegrationTest(config = SendVerifyEmailWorkflowServerConfig.class)
    public class DefaultMode extends SendVerifyEmailStepDefaultModeScenario {

        @Test
        @Override
        public void sendsDefaultVerifyEmailOnUserCreated() {
            super.sendsDefaultVerifyEmailOnUserCreated();
        }

        @Test
        @Override
        public void doesNotSendEmailWhenNoWorkflowIsConfigured() {
            super.doesNotSendEmailWhenNoWorkflowIsConfigured();
        }

        @Test
        @Override
        public void defaultLifespanMatchesRealmActionTokenSetting() throws Exception {
            super.defaultLifespanMatchesRealmActionTokenSetting();
        }

        @Test
        @Override
        public void lifespanConfigOverridesRealmDefault() throws Exception {
            super.lifespanConfigOverridesRealmDefault();
        }
    }

    @Nested
    @KeycloakIntegrationTest(config = SendVerifyEmailWorkflowServerConfig.class)
    public class CustomMessage extends SendVerifyEmailStepCustomMessageScenario {

        @Test
        @Override
        public void substitutesLinkAndUserVariablesInMessage() {
            super.substitutesLinkAndUserVariablesInMessage();
        }

        @Test
        @Override
        public void substitutesRealmVariablesInMessage() {
            super.substitutesRealmVariablesInMessage();
        }

        @Test
        @Override
        public void usesDefaultAccountNotificationSubjectWhenNotConfigured() {
            super.usesDefaultAccountNotificationSubjectWhenNotConfigured();
        }

        @Test
        @Override
        public void usesCustomSubjectWithVariableSubstitution() {
            super.usesCustomSubjectWithVariableSubstitution();
        }

        @Test
        @Override
        public void blankMessageFallsBackToDefaultVerifyEmail() {
            super.blankMessageFallsBackToDefaultVerifyEmail();
        }
    }

    @Nested
    @KeycloakIntegrationTest(config = SendVerifyEmailWorkflowServerConfig.class)
    public class ClientAndRedirectUri extends SendVerifyEmailStepClientAndRedirectUriScenario {

        @Test
        @Override
        public void usesSystemClientWhenClientIdNotConfigured() {
            super.usesSystemClientWhenClientIdNotConfigured();
        }

        @Test
        @Override
        public void redirectUriMatchingRegisteredClientUriIsIncludedInToken() throws Exception {
            super.redirectUriMatchingRegisteredClientUriIsIncludedInToken();
        }

        @Test
        @Override
        public void unknownClientIdPreventsEmailFromBeingSent() {
            super.unknownClientIdPreventsEmailFromBeingSent();
        }

        @Test
        @Override
        public void disabledClientPreventsEmailFromBeingSent() {
            super.disabledClientPreventsEmailFromBeingSent();
        }

        @Test
        @Override
        public void redirectUriNotRegisteredOnClientPreventsEmailFromBeingSent() {
            super.redirectUriNotRegisteredOnClientPreventsEmailFromBeingSent();
        }

        @Test
        @Override
        public void redirectUriWithoutClientIdPreventsEmailFromBeingSent() {
            super.redirectUriWithoutClientIdPreventsEmailFromBeingSent();
        }
    }

    @Nested
    @KeycloakIntegrationTest(config = SendVerifyEmailWorkflowServerConfig.class)
    public class ResetEmailVerification extends SendVerifyEmailStepResetEmailVerificationScenario {

        @Test
        @Override
        public void resetsEmailVerificationWhenEnabled() {
            super.resetsEmailVerificationWhenEnabled();
        }

        @Test
        @Override
        public void doesNotResetEmailVerificationByDefault() {
            super.doesNotResetEmailVerificationByDefault();
        }

        @Test
        @Override
        public void doesNotResetEmailVerificationWhenExplicitlyDisabled() {
            super.doesNotResetEmailVerificationWhenExplicitlyDisabled();
        }
    }

    @Nested
    @KeycloakIntegrationTest(config = SendVerifyEmailWorkflowServerConfig.class)
    public class UserEligibility extends SendVerifyEmailStepUserEligibilityScenario {

        @Test
        @Override
        public void doesNotSendEmailToUserWithoutEmailAddress() {
            super.doesNotSendEmailToUserWithoutEmailAddress();
        }

        @Test
        @Override
        public void doesNotSendEmailToDisabledUser() {
            super.doesNotSendEmailToDisabledUser();
        }
    }

    @Nested
    @KeycloakIntegrationTest(config = SendVerifyEmailWorkflowServerConfig.class)
    public class Scheduling extends SendVerifyEmailStepSchedulingScenario {

        @Test
        @Override
        public void delayedStepOnlyRunsOnceItIsDue() {
            super.delayedStepOnlyRunsOnceItIsDue();
        }

        @Test
        @Override
        public void adHocActivationRunsTheStepWithoutAnEventTrigger() {
            super.adHocActivationRunsTheStepWithoutAnEventTrigger();
        }
    }
}
