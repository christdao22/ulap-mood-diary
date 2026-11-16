# Security policy

Ulap is a mood diary. People write private, health-related things in it, so security reports are very welcome and taken seriously.

## Reporting a vulnerability

Please report it **privately**. Don't open a public issue, pull request or discussion for it.

- Email **security@ulap.app** (placeholder domain until release, see [PHASES.md Phase 10](PHASES.md#phase-10-release-v10)), or
- use GitHub's **Report a vulnerability** button on this repository's **Security** tab (private vulnerability reporting).

Please include:
- what you found and where (app version, server endpoint, or file and line),
- steps to reproduce it, or a proof of concept,
- what someone could do with it.

Use test accounts and your own data. **Never include real people's journal notes, emails or tokens** in a report.

## What happens next

| When | What |
|---|---|
| Within 3 days | We confirm we got your report |
| Within 10 days | We tell you whether we can reproduce it and how serious it looks |
| Within 90 days | We aim to fix it and release the fix. Serious issues come first. |

We'll keep you posted along the way and credit you in the release notes if you'd like. If the issue exposed users' personal data, we notify the National Privacy Commission and the people affected within 72 hours, as the Data Privacy Act requires ([privacy notice §9](docs/privacy-notice.md#9-keeping-your-data-safe)).

## Responsible disclosure

We won't take legal action against research done in good faith that:
- stays within your own accounts and data, or test accounts you made,
- doesn't read, change or delete other people's data, and stops as soon as you reach any,
- doesn't degrade the service (no denial-of-service, spam, or automated load testing against `api.ulap.app`),
- doesn't use social engineering or physical attacks, and
- gives us a reasonable time to fix the issue before you share it publicly. We'll agree a date with you, normally after the fix ships and no later than 90 days after your report.

## In scope

- The Ulap Android app (`app.ulap.android`)
- The Ulap API at `api.ulap.app` and `staging-api.ulap.app`
- The code in this repository

## Out of scope

- Reports from automated scanners without a working impact
- Missing security headers on pages that hold no user data
- Attacks that need a rooted phone, physical access to an unlocked phone, or a compromised Google account
- Third-party services Ulap uses (Google, Cloudflare, Sentry). Please report those to them directly.

Ulap has no bug bounty program.

## How Ulap handles data

See [README §14](README.md#14-privacy-safety-and-data-handling) for the security design and the [privacy notice](docs/privacy-notice.md) for what is kept and for how long.
