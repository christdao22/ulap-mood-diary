package app.ulap.server.auth

/**
 * The two emails the server sends, in the person's language (`Accept-Language`, else the account's
 * language, api-spec.md §2). Written to the voice guide (design-spec.md §10): warm, plain, no
 * pressure, and the "wasn't you?" line reassures instead of alarming. The link carries a token,
 * so a built message must never be logged.
 */
object AuthMail {
    /** `https://ulap.app/verify?token=…` (api-spec.md §6.5). */
    fun verificationLink(appLinkBase: String, token: String): String = "${appLinkBase.trimEnd('/')}/verify?token=$token"

    /** `https://ulap.app/reset?token=…` (api-spec.md §6.7). */
    fun resetLink(appLinkBase: String, token: String): String = "${appLinkBase.trimEnd('/')}/reset?token=$token"

    fun verification(to: String, language: String, link: String): MailMessage = when (language) {
        "fil" -> MailMessage(
            to = to,
            subject = "Kumpirmahin ang iyong email para sa Ulap",
            text = """
                |Kumusta,
                |
                |Salamat sa pagsali sa Ulap. Para ma-backup ang iyong mga check-in, pakikumpirma ang email address na ito:
                |
                |$link
                |
                |Isang beses lang gumagana ang link at mag-e-expire ito sa loob ng 24 oras. Kung hindi ikaw ang gumawa ng Ulap account, maaari mong balewalain ang email na ito. Wala kang kailangang gawin.
                |
                |Ingat,
                |Ulap
            """.trimMargin(),
        )

        "ceb" -> MailMessage(
            to = to,
            subject = "Kumpirmaha ang imong email para sa Ulap",
            text = """
                |Kumusta,
                |
                |Salamat sa pag-apil sa Ulap. Aron ma-backup ang imong mga check-in, palihug kumpirmaha kini nga email address:
                |
                |$link
                |
                |Kausa ra mogana ang link ug ma-expire kini sulod sa 24 ka oras. Kung dili ikaw ang naghimo sa Ulap account, mahimo nimo kining ibaliwala. Wala kay kinahanglang buhaton.
                |
                |Pag-amping,
                |Ulap
            """.trimMargin(),
        )

        else -> MailMessage(
            to = to,
            subject = "Confirm your email for Ulap",
            text = """
                |Hi,
                |
                |Thanks for joining Ulap. So your check-ins can be backed up, please confirm this email address:
                |
                |$link
                |
                |The link works once and expires in 24 hours. If you didn't create an Ulap account, you can ignore this email. You don't need to do anything.
                |
                |Take care,
                |Ulap
            """.trimMargin(),
        )
    }

    fun passwordReset(to: String, language: String, link: String): MailMessage = when (language) {
        "fil" -> MailMessage(
            to = to,
            subject = "I-reset ang iyong Ulap password",
            text = """
                |Kumusta,
                |
                |May natanggap kaming kahilingan na i-reset ang password ng Ulap account na ito. Kung ikaw iyon, pumili ng bagong password dito:
                |
                |$link
                |
                |Isang beses lang gumagana ang link at mag-e-expire ito sa loob ng 30 minuto. Kung hindi ikaw ang humiling nito, maaari mong balewalain ang email na ito. Hindi magbabago ang iyong password.
                |
                |Ingat,
                |Ulap
            """.trimMargin(),
        )

        "ceb" -> MailMessage(
            to = to,
            subject = "I-reset ang imong Ulap password",
            text = """
                |Kumusta,
                |
                |Adunay among nadawat nga hangyo nga i-reset ang password niini nga Ulap account. Kung ikaw kadto, pagpili og bag-ong password dinhi:
                |
                |$link
                |
                |Kausa ra mogana ang link ug ma-expire kini sulod sa 30 ka minuto. Kung dili ikaw ang nangayo niini, mahimo nimo kining ibaliwala. Dili mausab ang imong password.
                |
                |Pag-amping,
                |Ulap
            """.trimMargin(),
        )

        else -> MailMessage(
            to = to,
            subject = "Reset your Ulap password",
            text = """
                |Hi,
                |
                |We got a request to reset the password for this Ulap account. If that was you, choose a new password here:
                |
                |$link
                |
                |The link works once and expires in 30 minutes. If you didn't ask for this, you can ignore this email. Your password stays the same.
                |
                |Take care,
                |Ulap
            """.trimMargin(),
        )
    }
}
