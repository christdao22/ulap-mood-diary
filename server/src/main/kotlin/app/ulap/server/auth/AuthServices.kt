package app.ulap.server.auth

import app.ulap.server.common.RateLimitSettings
import app.ulap.server.config.AppConfig
import app.ulap.server.db.Db
import java.time.Clock

/**
 * The parts of auth that talk to the outside world or cost real work, built once from the
 * config. Tests hand in fakes: a recording [Mailer], a [GoogleTokenVerifier] that accepts known
 * tokens, a lighter [PasswordHasher].
 */
class AuthComponents(
    val accessTokens: AccessTokens,
    val googleVerifier: GoogleTokenVerifier,
    val mailer: Mailer,
    val passwordHasher: PasswordHasher,
    val commonPasswords: CommonPasswords,
) {
    companion object {
        fun from(config: AppConfig, clock: Clock): AuthComponents = AuthComponents(
            accessTokens = AccessTokens(config.jwt, clock),
            googleVerifier = GoogleTokenVerifier.from(config.googleWebClientId),
            mailer = SmtpMailer(config.mail),
            passwordHasher = Argon2PasswordHasher(),
            commonPasswords = CommonPasswords.load(),
        )
    }
}

/** The auth services, wired together (ulap-reference.md §6.1). Routes get this. */
class AuthServices(db: Db, config: AppConfig, clock: Clock, rateLimits: RateLimitSettings, components: AuthComponents) {
    val limits: AuthLimits = AuthLimits(rateLimits, clock)
    val passwords: PasswordPolicy = PasswordPolicy(components.passwordHasher, components.commonPasswords)
    val sessions: SessionService = SessionService(db, clock, components.accessTokens, config.jwt.refreshTokenTtlDays)
    val emails: AccountEmailService =
        AccountEmailService(db, clock, components.mailer, config.appLinkBase, limits, passwords)
    val signIn: SignInService =
        SignInService(db, components.googleVerifier, passwords, limits, sessions, emails)
}
