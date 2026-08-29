# Security Policy

## Supported Versions

Naomi has not had a stable release. The only supported version is whatever is
currently on `main`.

| Version | Supported          |
| ------- | ------------------ |
| `main`  | :white_check_mark: |
| 0.1.0   | :x:                |

---

## Reporting a Vulnerability

The Naomi project takes privacy and security extremely seriously. Because Naomi handles personal thoughts, voice notes, and structured knowledge on-device, safeguarding user data is our highest priority.

### How to Report

1. **Do NOT open a public issue** on GitHub for security or sensitive privacy vulnerabilities.
2. Please submit vulnerability reports via **GitHub Private Vulnerability Reporting** directly under the repository's **Security** tab (`Security > Advisories > Report a vulnerability`).
3. Include detailed steps to reproduce the vulnerability, along with proof of concept where applicable.

### Scope

Naomi stores everything locally and declares no `INTERNET` permission, so there
is no server, no account and no network surface to attack. The realistic threat
model is local: another app or an attacker with physical or `adb` access reading
the memory database. Reports about that surface — exported components, backup
and extraction rules, `FLAG_MUTABLE` pending intents, debuggable builds, unsafe
`content://` handling — are exactly what this policy is for.

### What to Expect

- You will receive an initial response within 48 hours acknowledging receipt of your report.
- We will work closely with you to validate, patch, and release a security advisory before public disclosure.
- Security researchers who follow responsible disclosure will be credited in our release notes.
