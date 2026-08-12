# Security Policy

*[Version française](fr/SECURITY.FR.md)*

## Supported Versions

Security fixes are provided only for the [latest release](https://github.com/jul-m/keycloak-workflow-send-verify-email/releases), and only for the Keycloak versions listed in the [README compatibility table](../README.md#compatibility). Older releases do not receive security fixes; please upgrade to the latest release and, if your Keycloak version has since been dropped from the range, to a supported one.

## Reporting a Vulnerability

Please do **not** report security vulnerabilities through public GitHub issues, discussions, or pull requests.

Instead, report it privately using [GitHub Security Advisories](https://github.com/jul-m/keycloak-workflow-send-verify-email/security/advisories/new) — this lets us coordinate a fix and disclosure with you directly.

Please include as much of the following as you can:

- A description of the vulnerability and its potential impact
- Steps to reproduce, or a proof of concept
- The affected version(s) of this extension and of Keycloak
- Any suggested mitigation, if known

## What to Expect

We will make a best effort to:

- Acknowledge your report
- Investigate and keep you informed of progress toward a fix
- Publish a new release and a security advisory once a fix is available, crediting you unless you prefer to remain anonymous

## Scope

This project is a Keycloak provider (a JAR deployed into `providers/`) and does not run as its own service. Vulnerabilities in Keycloak itself should be reported to the [Keycloak project](https://www.keycloak.org/security) instead.
