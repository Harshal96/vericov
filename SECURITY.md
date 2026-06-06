# Security Policy

## Reporting a Vulnerability

Do not open a public issue for a suspected vulnerability or exposed secret.
Use the repository's
[private vulnerability reporting](https://github.com/Harshal96/vericov/security/advisories/new)
to share reproduction steps, affected versions, impact, and any suggested
mitigation.

You should receive an acknowledgement within five business days. Maintainers
will coordinate validation, remediation, disclosure timing, and credit with the
reporter.

## Supported Versions

Vericov is currently pre-1.0. Security fixes are made on the latest release and
the `main` branch. Older snapshots are not supported.

## Deployment Responsibility

Vericov services bind to localhost by default. Operators are responsible for
TLS, authentication, network exposure, backups, and secret rotation in their
deployment. Never expose development auth bypass outside a trusted private
network.
