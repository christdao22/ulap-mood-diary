@file:OptIn(ExperimentalUuidApi::class)

package app.ulap.server.auth

import app.ulap.server.common.ApiException
import app.ulap.server.common.RateLimits
import app.ulap.shared.api.Api
import app.ulap.shared.api.EmailLoginRequest
import app.ulap.shared.api.EmailRegisterRequest
import app.ulap.shared.api.ForgotPasswordRequest
import app.ulap.shared.api.GoogleSignInRequest
import app.ulap.shared.api.LogoutRequest
import app.ulap.shared.api.RefreshRequest
import app.ulap.shared.api.ResetPasswordRequest
import app.ulap.shared.api.VerifyEmailRequest
import app.ulap.shared.validation.AccountRules
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The routes under `/v1/auth` (api-spec.md §6). Routes parse the body and headers, call a service and respond;
 * every failure is an [ApiException] the service threw (ulap-reference.md §6.1). Request bodies
 * here hold passwords and tokens: nothing in this file logs.
 */
fun Route.authRoutes(services: AuthServices) {
    val signIn = services.signIn
    val sessions = services.sessions
    val emails = services.emails

    route("/auth") {
        // 30 per minute by IP + device (api-spec.md §10), on top of the default limit.
        rateLimit(RateLimits.AUTH_TOKEN) {
            post("/google") {
                val request = call.receive<GoogleSignInRequest>()
                call.respond(signIn.signInWithGoogle(request, call.signInContext()))
            }
            post("/refresh") {
                val request = call.receive<RefreshRequest>()
                call.respond(sessions.refresh(request.refreshToken, call.deviceId()))
            }
        }
        post("/email/register") {
            val request = call.receive<EmailRegisterRequest>()
            call.respond(HttpStatusCode.Created, signIn.register(request, call.signInContext()))
        }
        post("/email/login") {
            val request = call.receive<EmailLoginRequest>()
            call.respond(signIn.login(request, call.signInContext()))
        }
        post("/email/verify") {
            emails.verify(call.receive<VerifyEmailRequest>().token)
            call.respond(HttpStatusCode.NoContent)
        }
        post("/password/forgot") {
            emails.forgotPassword(call.receive<ForgotPasswordRequest>().email, call.emailLanguage())
            call.respond(HttpStatusCode.NoContent)
        }
        post("/password/reset") {
            val request = call.receive<ResetPasswordRequest>()
            emails.resetPassword(request.token, request.newPassword)
            call.respond(HttpStatusCode.NoContent)
        }
        signedInAuthRoutes(services)
    }
}

/** The 🔒 auth routes: a valid access token names the caller (api-spec.md §6.6, §6.10–§6.13). */
private fun Route.signedInAuthRoutes(services: AuthServices) {
    val signIn = services.signIn
    val sessions = services.sessions
    val emails = services.emails

    authenticated {
        post("/email/resend-verification") {
            emails.resendVerification(call.userPrincipal().userId, call.emailLanguage())
            call.respond(HttpStatusCode.NoContent)
        }
        post("/logout") {
            sessions.logout(call.userPrincipal(), call.receive<LogoutRequest>().refreshToken)
            call.respond(HttpStatusCode.NoContent)
        }
        post("/logout-all") {
            sessions.logoutAll(call.userPrincipal().userId)
            call.respond(HttpStatusCode.NoContent)
        }
        post("/link/google") {
            val request = call.receive<GoogleSignInRequest>()
            call.respond(signIn.linkGoogle(call.userPrincipal().userId, request.idToken))
        }
        post("/link/email") {
            val request = call.receive<EmailRegisterRequest>()
            call.respond(signIn.linkEmail(call.userPrincipal().userId, request, call.emailLanguage()))
        }
    }
}

private fun ApplicationCall.signInContext() = SignInContext(deviceId(), RateLimits.clientIp(this), emailLanguage())

/** `X-Device-Id` (api-spec.md §2, Required headers): the device a session belongs to. */
private fun ApplicationCall.deviceId(): Uuid {
    val header = request.headers[Api.HEADER_DEVICE_ID]?.trim()
        ?: throw ApiException.badRequest("${Api.HEADER_DEVICE_ID} header is required")
    return runCatching { Uuid.parse(header) }.getOrNull()
        ?: throw ApiException.badRequest("${Api.HEADER_DEVICE_ID} header must be a UUID")
}

/** `Accept-Language`, used only for email text (api-spec.md §2). Null when absent or not one of ours. */
private fun ApplicationCall.emailLanguage(): String? =
    AccountRules.languageFromTag(request.headers[HttpHeaders.AcceptLanguage]?.substringBefore(','))
