# Keycloak Workflow Step: `send-verify-email`

[![CI](https://github.com/jul-m/keycloak-workflow-send-verify-email/actions/workflows/ci.yml/badge.svg)](https://github.com/jul-m/keycloak-workflow-send-verify-email/actions/workflows/ci.yml)
[![CodeQL](https://github.com/jul-m/keycloak-workflow-send-verify-email/actions/workflows/codeql.yml/badge.svg)](https://github.com/jul-m/keycloak-workflow-send-verify-email/actions/workflows/codeql.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

*[Version française](README.fr.md)*

A Keycloak extension that adds a workflow step, `send-verify-email`, which sends the user a
signed email-verification link — the same action link the native Admin API endpoint
`send-verify-email` produces — from any [Keycloak workflow](https://www.keycloak.org/docs/latest/server_admin/#_workflows).

- **Contents**
  - [Why this step](#why-this-step)
  - [Requirements](#requirements)
  - [Installation](#installation)
  - [Compatibility](#compatibility)
  - [Quick start](#quick-start)
  - [Configuration reference](#configuration-reference)
  - [Sending modes](#sending-modes)
  - [Template variables](#template-variables)
  - [Examples](#examples)
  - [Behavior and limitations](#behavior-and-limitations)
  - [Troubleshooting](#troubleshooting)
  - [Contributing](#contributing)
  - [Security](#security)
  - [License](#license)

## Why this step

Keycloak's built-in workflow steps can send a plain notification to a user (`notify-user`), but
none of them can send an *actionable* link that lets the user verify their email address, set a
password, and complete the required actions on their account. Triggering that today means calling
the Admin API `send-verify-email` endpoint from an external script.

This step brings that capability inside Keycloak's own workflow engine, so scenarios like the
following need no external automation:

- **Onboarding** — when an account is provisioned (`user-created`, or when the user joins a group),
  automatically email a verification link that also lets the user set their password.
- **Reminders** — re-send a verification link on a schedule to users who never confirmed their
  address.
- **Re-verification** — reset `emailVerified` and ask the user to confirm their address again after
  a change of policy or of email provider.

Compared to a hand-rolled `notify-user` message, the link is a real signed action token: it is
validated, scoped to a client, has a configurable lifespan and an optional post-verification
redirect. User, client and redirect-URI validation, as well as all default values, are delegated to
the same internal code path as the native Admin API (`UserResource.verifySendEmailParams`), so
behavior stays consistent with the rest of Keycloak.

## Requirements

| Requirement | Details |
| --- | --- |
| Keycloak | See the [compatibility table](#compatibility) below. |
| `workflows` feature | A supported Keycloak feature, enabled by default — no `--features=workflows` flag needed (unless an administrator has explicitly disabled it). |
| Realm SMTP | The realm must have a working **Realm settings → Email** configuration; the step sends mail through Keycloak's own email provider. |
| Realm `frontendUrl` | **Required.** Workflow steps run outside of an incoming HTTP request, so the step always builds the verification link from the realm's `frontendUrl` rather than from a request URI. Without it, the step fails at runtime — see [Behavior and limitations](#behavior-and-limitations). |
| Java | Java 17 (JDK), to build from source only. Running the released JAR requires nothing beyond Keycloak itself. |

## Installation

1. Download the JAR and its `.sha256` checksum file from the
   [Releases](https://github.com/jul-m/keycloak-workflow-send-verify-email/releases) page. Each
   release ships a **single JAR** covering every Keycloak version listed in its release notes; the
   extension's own version number never encodes a Keycloak version.
2. Verify the checksum before deploying:

   ```bash
   sha256sum -c keycloak-workflow-send-verify-email-<version>.jar.sha256
   ```
3. Copy the JAR into Keycloak's `providers/` directory.
4. Rebuild the server augmentation and restart Keycloak:

   ```bash
   bin/kc.sh build
   bin/kc.sh start
   ```

To confirm the step is registered, check that `send-verify-email` is listed under the
`workflow-step` provider category:

- **Admin Console**: switch to the **master** realm, open **Server info**, and check the
  **Provider info** tab.
- **REST API**:

  ```bash
  curl -s -H "Authorization: Bearer $TOKEN" \
    "https://keycloak.example.com/admin/serverinfo" \
    | jq '.providers."workflow-step".providers | keys'
  ```

## Compatibility

<!-- kc-compat:start -->
| Keycloak | Extension |
| --- | --- |
| 26.7.x | Latest release (see [Releases](https://github.com/jul-m/keycloak-workflow-send-verify-email/releases) for older extension versions and their supported range) |
<!-- kc-compat:end -->

This extension supports Keycloak 26.7 and later, as declared in
[`scripts/kc-versions.sh`](scripts/kc-versions.sh) — the authoritative source for the table above. Every listed version is rebuilt and re-tested on each
release, and each GitHub release states the exact range it was validated against. New Keycloak
releases are tested automatically against real integration tests (see
[`new-keycloak-version.yml`](.github/workflows/new-keycloak-version.yml)) and added to the supported
range through a reviewed pull request, not pushed directly.

## Quick start

The simplest way to create a workflow is through the Admin Console. Open your realm's
**Workflows** page and create a new workflow: the console's editor takes the definition as YAML
text, not a form with one field per step parameter — even though the step declares the properties
listed in the [configuration reference](#configuration-reference) below, the console does not
render them individually. Paste the following; it emails a verification link to every newly
created user, using Keycloak's standard verify-email template:

```yaml
name: Verify email on user creation
on: user-created
steps:
  - uses: send-verify-email
```

Create a user with an email address in that realm, and they receive the verification email.

For scripting or automation, the same definition can be posted through the Admin REST API instead,
which accepts both YAML and JSON. Save it as `workflow.yml` and run:

```bash
curl -X POST "https://keycloak.example.com/admin/realms/myrealm/workflows" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/yaml" \
  --data-binary @workflow.yml
```

> `$TOKEN` is an admin access token for the target realm. `Content-Type: application/json` is
> accepted as well if you prefer to post the JSON equivalent.

## Configuration reference

The step applies to **user** resources. All parameters are optional:

| Parameter | Type | Default | Description |
| --- | --- | --- | --- |
| `message` | string | *(none)* | Custom email body. Setting it switches the step to [custom-message mode](#sending-modes). Supports [template variables](#template-variables). |
| `subject` | string | `accountNotificationSubject` | Subject used in custom-message mode. Normally a Keycloak message key; supports [template variables](#template-variables). |
| `client_id` | string | The system client used by the native Admin API | Client used to validate `redirect_uri` and recorded in the action token. |
| `redirect_uri` | string | *(none)* | Where to send the user after successful verification. Validated against `client_id`'s registered redirect URIs. Supports [template variables](#template-variables). |
| `lifespan` | integer (seconds) | The realm's `action-token-generated-by-admin` lifespan | Validity of the generated action token. |
| `reset_email_verification` | boolean | `false` | If `true`, sets `emailVerified` to `false` and adds the built-in `VERIFY_EMAIL` required action before sending. |

## Sending modes

The presence of `message` selects one of two mutually exclusive modes.

**Default mode** (no `message`) delegates to `EmailTemplateProvider.sendVerifyEmail`, which uses the
theme's standard verify-email template. The email is byte-for-byte what the native Admin API
`send-verify-email` sends, so any theme customization you already have keeps working unchanged.

**Custom-message mode** (with `message`) renders the workflow notification template
(`workflow-notification.ftl`, the same one `notify-user` uses) with your message as the body, after
substituting [template variables](#template-variables) in both `message` and `subject`.

In custom-message mode, `subject` is resolved like `notify-user`'s: it is normally a message key
looked up in the theme's message bundle. Variables are substituted first; if the resulting value
matches no key in the bundle, it is used verbatim as the subject line. That makes both of these
work:

```yaml
subject: emailVerificationSubject          # resolved from the theme's message bundle
subject: Verify your ${realm.name} account # used as literal text
```

## Template variables

The following variables can be used in `message`, `subject` and `redirect_uri`:

| Variable | Value |
| --- | --- |
| `${link}` | The signed email-verification link. **Not available in `redirect_uri`**, which is resolved and validated before the link is generated. |
| `${user.<attribute>}` | First value of the user attribute `<attribute>`, as with `notify-user`. Covers profile fields (`username`, `email`, `firstName`, `lastName`) and custom attributes. |
| `${realm.name}` | The realm's technical name. |
| `${realm.displayName}` | The realm's display name. |
| `${realm.frontendUrl}` | The `frontendUrl` configured on the realm, if any. |
| `${realm.baseUrl}` | Keycloak's computed base URL, e.g. `https://keycloak.example.com/realms`. |
| `${realmFullBaseUrl}` | Full realm URL, including the realm name, e.g. `https://keycloak.example.com/realms/myrealm`. |

An unknown variable resolves to nothing and is left as-is in the output — check for a literal
`${...}` in a received email if a value looks missing.

Reading a **custom** user attribute (one not declared in the realm's user profile) additionally
requires the realm's unmanaged attribute policy to allow it — otherwise `${user.department}` and
the like resolve to nothing.

## Examples

A complete onboarding workflow: a custom message with a 48-hour link, a redirect to the account
console, and `emailVerified` reset so the user must confirm their address.

```yaml
name: Onboarding new users
on: user-created
steps:
  - uses: send-verify-email
    with:
      subject: Verify your ${realm.name} account
      lifespan: "172800"
      reset_email_verification: true
      client_id: account
      redirect_uri: ${realmFullBaseUrl}/account
      message: >-
        <p>Dear ${user.firstName} ${user.lastName},</p>

        <p>An account has been created for you by your administrator.</p>

        <p><a href="${link}">Click here</a> to verify your email address, set your
        password and activate your account. This link is valid for 48 hours.</p>

        <p>Once it expires, you can still activate your account from your
        <a href="${realmFullBaseUrl}/account">account page</a>, using the username
        "${user.username}" or your email address, together with the temporary
        password set by your administrator — or via the "Forgot password" link.</p>

        <p>Best regards,<br/>The technical team</p>
```

More ready-to-post definitions are available in [`docs/examples/`](docs/examples/).

## Behavior and limitations

- **`frontendUrl` is required in practice.** Workflow steps run outside of an incoming HTTP
  request — whether triggered by an event such as `user-created` or by a scheduled trigger — so
  there is normally no request URI to build the link from. The step falls back to the realm's
  `frontendUrl`; if none is set, it fails with an explicit error rather than emitting a broken or
  relative link.
- **Ineligible users are skipped silently.** Eligibility is delegated to
  `UserResource.verifySendEmailParams`, which requires the user to be enabled and to have an email
  address. When either check fails, the workflow engine catches and logs the error: no email is
  sent, and nothing else — user creation included — is disrupted.
- **Delivery failures do not fail the workflow.** An SMTP error is logged at `ERROR` level; the
  workflow keeps going. Watch the server log for `Failed to send verify email to user ...`.
- **`reset_email_verification` applies before sending** and is not rolled back if the send then
  fails. The user is left with `emailVerified = false` and the `VERIFY_EMAIL` required action, which
  keeps them blocked at login until a link reaches them — re-run the workflow, or use the Admin API,
  to send a new one.
- **Custom-message mode ignores the theme's verify-email template.** It renders the workflow
  notification template instead; style your HTML accordingly.

## Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| `send-verify-email` is missing from the master realm's Provider info tab or from `serverinfo` | JAR not in `providers/`, or `bin/kc.sh build` not re-run after copying it. |
| No email at all, no error in the log | The user is disabled or has no email address — see [Behavior and limitations](#behavior-and-limitations). |
| `Failed to send verify email to user ...` in the log | Realm SMTP settings are missing or wrong. Test them from **Realm settings → Email**. |
| `Cannot generate a verify-email link from an asynchronous workflow` | The workflow ran outside an HTTP request and the realm has no `frontendUrl`. Set it in **Realm settings → General**. |
| Links point to the wrong host (e.g. an internal address) | Set the realm's `frontendUrl`, or Keycloak's `hostname` option, to the externally reachable URL. |
| The email contains a literal `${user.something}` | Unknown variable, or a custom attribute blocked by the realm's unmanaged attribute policy. |
| `400 Bad Request` when creating the workflow | Invalid redirect URI for the chosen `client_id`, or an unknown step parameter. The response body names the offending value. |

## Contributing

Contributions are welcome. See [CONTRIBUTING.md](docs/CONTRIBUTING.md) for local setup, coding
conventions, the test structure and the pull request process, and
[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for community guidelines.

## Security

Please do not report vulnerabilities through public issues. See
[SECURITY.md](docs/SECURITY.md) for the disclosure process.

## License

Distributed under the [Apache License 2.0](LICENSE).
