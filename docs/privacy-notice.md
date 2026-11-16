# Ulap: Mood Diary · Privacy Notice

> 📚 **Ulap docs:** [README](../README.md) · [Phases](../PHASES.md) · [Technical plan](technical-plan.md) · [API reference](api-spec.md) · [Design spec](design-spec.md) · [Repo reference](ulap-reference.md) · **Privacy notice**
>
> **This document owns:** the wording of Ulap's privacy notice (English source), the data retention periods it promises, and the account deletion steps. Everything below the line is what users read.
>
> **Keep in sync.** When this notice changes, update in the same pull request:
> 1. Run `python web/build_privacy.py` to rebuild [web/privacy.html](../web/privacy.html) (the published copy, for Google Play's privacy policy link). Update [web/delete-account.html](../web/delete-account.html) by hand if §6 or §8 changed.
> 2. The short in-app version: the `privacy_*` strings in all three `strings.xml` files.
> 3. **Effective date** below, and `PrivacyConsent.CURRENT_NOTICE_VERSION` in `core/model` if people should agree again. The app records which version each person agreed to.
>
> **Before release (PHASES.md Phase 10):** replace the placeholder domain `ulap.app` and its mailboxes, name the hosting and email providers in §5, and check with the NPC (see [NPC registration](#npc-registration-maintainer-notes) at the end).

---

**Effective date:** 16 November 2026 (version `2026-11-16`)

Ulap is a mood diary. You check in with how you feel, and Ulap keeps your diary private to you. This notice explains what Ulap keeps, why, where, for how long, and how to delete it. It follows the Philippine **Data Privacy Act of 2012 (Republic Act No. 10173)**.

Ulap is a self-care diary, not a medical or emergency service. If you need to talk to someone now, call the **NCMH Crisis Hotline at 1553**, open 24/7.

## 1. Who is responsible

Ulap is made by **Christian Daohog**, who is the personal information controller for Ulap and acts as its Data Protection Officer.

- Privacy questions and requests: **privacy@ulap.app**
- Security issues: **security@ulap.app** (see [SECURITY.md](../SECURITY.md))

## 2. What Ulap keeps

**If you use Ulap without an account,** everything stays on your phone. Nothing about your check-ins is sent to Ulap's server. The only thing that can leave your phone is a crash report (see below).

**On your phone:**
- Your check-ins: the mood you picked, energy (1–10), stress (1–10), stress after a calming exercise, what it was about (a trigger, or your own words), your journal note, which support you used, whether you saved it as a good moment, and the date, time and time zone.
- Your settings: language, whether Ulap the cloud is shown, and when you agreed to this notice.
- A random ID for this install. It doesn't identify you or your phone outside Ulap.

**If you sign in to back up and sync,** Ulap's server also keeps:
- Your sign-in details: your email address; for Google sign-in, Google's ID for your account; for email sign-in, a scrambled form (hash) of your password, never the password itself.
- Your name, only if you add one, plus your language and Ulap the cloud setting.
- Copies of your check-ins, as listed above.
- Sign-in sessions, stored in scrambled form, with the install ID they belong to.

**Crash reports.** When the app or server crashes, a report goes to our crash-reporting service. It holds technical details: the app version, Android version, phone model and where in the code the crash happened. Crash reports never include your journal notes, your own trigger words, your email, your name or sign-in tokens.

**Server logs** record which part of the server was called, the result, and how long it took. They never include your check-ins, notes, passwords or tokens. To stop misuse, the server briefly uses your network (IP) address to limit how many requests come in. It keeps that only in memory and doesn't save it.

Ulap doesn't use your contacts, location, camera, microphone or advertising ID.

## 3. Why Ulap keeps it

Ulap uses your information only to:
- show you your diary, logs and reports,
- back up and sync your diary between your phones, if you sign in,
- send emails you asked for: confirming your email address and resetting your password,
- keep your account secure and stop misuse, and
- find and fix crashes.

Your check-ins are health-related, so the law treats them as **sensitive personal information**. Ulap keeps and uses them because you agreed to it (RA 10173, Section 13(a)). You agree when you tap **Start my first check-in**, and the app records when you agreed and to which version of this notice.

Reports and "Ulap noticed" insights are worked out on your phone. Ulap makes no decisions about you on its own and doesn't build a profile of you.

## 4. What Ulap never does

- No ads.
- No selling your data, and no sharing it with advertisers or data brokers.
- No reading or analyzing your journal notes for anything other than showing them to you. Search in Logs happens on your phone.

## 5. Where it's kept, and who else handles it

**On your phone,** your diary is stored in an encrypted database. Its key is protected by your phone's secure hardware (Android Keystore). Ulap turns off Android's cloud backup for the diary.

**On Ulap's server,** everything travels over an encrypted connection (HTTPS). The server's disk and its backups are encrypted, and your journal notes and own trigger words are encrypted again on top of that, with a key kept separate from the database. Each account can only reach its own check-ins.

Ulap uses a few service providers to run. They handle data only on Ulap's behalf and only as needed:

| Provider | What it does | What it sees |
|---|---|---|
| Server hosting | Runs Ulap's server and database | Everything stored on the server, encrypted at rest |
| Cloudflare | Carries traffic to the server, and stores the encrypted backups | Encrypted traffic; backups it can't read |
| Google | Signs you in, only if you choose "Continue with Google" | Your Google account, as with any Google sign-in |
| Email service | Sends confirmation and password reset emails | Your email address and the email |
| Sentry | Collects crash reports | Crash details as described in §2, never notes or emails |

Some of these providers keep data outside the Philippines. Ulap stays responsible for your data wherever it's kept, and uses only providers that agree to protect it at least as well as Philippine law requires.

## 6. How long Ulap keeps it

| What | How long |
|---|---|
| Check-ins on your phone | Until you delete them, delete your account, or remove the app or its data |
| Check-ins on the server | Until you delete them or your account |
| A deleted check-in | Its note and own trigger words are erased right away. The rest is erased after 90 days (it's kept that long only so your other phones also delete it). |
| Your account | Until you delete it |
| Sign-in sessions | Up to 60 days after last use |
| Email confirmation links | 24 hours, then they stop working |
| Password reset links | 30 minutes, then they stop working |
| Server logs | Up to 30 days |
| Crash reports | Up to 90 days |
| **Backups** | **Each daily backup is encrypted and deleted after 30 days.** So anything you delete is gone from every backup within 30 days. |

## 7. Your rights

Under the Data Privacy Act you have the right to:
- **be informed** about how your data is used (this notice),
- **access** your data: your whole diary is in the app; for a copy of what the server keeps, email us,
- **correct** it: edit any check-in in Logs,
- **delete** it: delete any check-in in Logs, or your whole account (§8),
- **object** and **withdraw consent**: delete your account, or remove the app and its data. You don't need to give a reason,
- **data portability**: ask us by email for a copy in a common format (an in-app export is planned),
- **complain** to the National Privacy Commission at [privacy.gov.ph](https://privacy.gov.ph), and claim damages where the law allows.

Email **privacy@ulap.app** for any of these. We'll reply within 15 days, and may ask you to confirm the request from the email on your account, so no one else can ask for your data.

## 8. Deleting your account and data

**In the app:** open **Account** → **Delete account and all data**, then confirm. This permanently erases your account and every check-in on the server, then clears Ulap on your phone. It happens right away, and needs an internet connection. If you have no account, the same option deletes the diary on your phone. If Ulap asks you to sign in again first, that's so it knows it's you. Until you do, it can only delete the diary on this phone, and your backup stays on the server.

**Without the app:** email **privacy@ulap.app** from the email address on your Ulap account, with the subject **"Delete my Ulap account"**. We'll confirm it's you, delete your account and all its data within 7 days, and reply when it's done.

**What's deleted:** your account, sign-in details, sessions, settings and every check-in, notes included.

**What's kept, and for how long:** nothing stays in Ulap's working database. Encrypted backups made before the deletion expire within 30 days. Crash reports, which never hold your notes or email, expire within 90 days.

**No account?** Your diary is only on your phone. To delete it, use **Account** → **Delete account and all data**, or remove the app or clear its data in Android settings.

## 9. Keeping your data safe

Ulap protects your data with encryption on your phone and on the server, scrambled passwords, short-lived sign-in tokens, and access only for the people who run Ulap. If a breach ever puts your data at risk, we'll tell you and the National Privacy Commission within 72 hours of finding out, as the law requires.

## 10. Changes to this notice

When this notice changes, the effective date above changes too. If a change affects how your data is used, the app will show you the new notice and ask you to agree again before anything new happens with your data.

## 11. Contact

**Christian Daohog**, Ulap: Mood Diary
Privacy and Data Protection Officer: **privacy@ulap.app**

---

## NPC registration (maintainer notes)

Not part of the notice. Checked 16 November 2026 against **NPC Circular No. 2022-04** (registration of data processing systems and DPOs, in force since January 2023). This is a reading of the rules, not legal advice. Confirm with the NPC (or a lawyer) before release.

**Who must register (Circular 2022-04, Section 5).** A personal information controller or processor must register its data processing systems if it meets **any one** of these:
1. it employs **250 or more** people,
2. it processes the **sensitive personal information of 1,000 or more** people, or
3. its processing is **likely to pose a risk** to people's rights and freedoms.

Systems that make automated decisions or profiles must always be registered. Ulap does neither (reports run on the phone and only describe patterns).

**How it applies to Ulap.**
- Mood check-ins and journal notes are about a person's health, so they're **sensitive personal information** (RA 10173, Section 3(l)).
- Ulap has one maintainer, so (1) doesn't apply.
- (2) applies once **1,000 people** have signed in to back up. Guest diaries stay on the phone and never reach Ulap, but synced accounts do. 1,000 is a realistic number soon after a public Play release.
- (3) is a judgment call. Mental-health data at scale could count as likely to pose a risk.

**What to do.**
1. **Register before the public release** (PHASES.md Phase 10), not after reaching 1,000 accounts. Voluntary registration is allowed (Section 6) and avoids missing the 20-day deadline that runs from when a covered system starts or a DPO is appointed (Section 7). Registration goes through the NPC Registration System (NPCRS) and is renewed every year.
2. **Appoint a DPO** and register them. While Ulap has one maintainer, that's the owner, reachable at privacy@ulap.app.
3. Before registering, write a short **Privacy Impact Assessment** for the sync server, and a **breach response plan** (notify the NPC and affected people within 72 hours).
4. If Ulap stays unregistered, the circular expects a notarized sworn declaration and undertaking (Annex 1) instead.

Sources: [NPC Circular No. 2022-04 (PDF)](https://privacy.gov.ph/wp-content/uploads/2023/05/Circular-2022-04-1.pdf) · [NPC FAQs for PICs and PIPs](https://privacy.gov.ph/pips-and-pics/faqs/)
