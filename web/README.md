# web

Ulap's public pages for `https://ulap.app` (placeholder domain). Static files, no build step.

| File | What it's for |
|---|---|
| `privacy.html` | The privacy notice. Google Play's privacy policy link. Generated from [docs/privacy-notice.md](../docs/privacy-notice.md): edit that, then regenerate. |
| `delete-account.html` | Google Play's account deletion link (Data safety form). Follows privacy notice §8. |
| `.well-known/security.txt` | Security contact (RFC 9116). Renew `Expires` every year. |
| `build_privacy.py` | Builds `privacy.html` from the notice. |
| `style.css` | Shared styles, from design-spec §2.1 and §3. |

Later, the same site hosts `.well-known/assetlinks.json` for App Links and the small `/verify` and `/reset` pages (api-spec §6.5). Hosting is chosen in PHASES.md Phase 10.

To regenerate `privacy.html` after editing the notice: `pip install markdown`, then `python web/build_privacy.py` from the repo root.
