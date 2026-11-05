package app.ulap.shared.api

import kotlinx.serialization.Serializable

/** Body of `GET /v1/health` (`ok`) and `GET /v1/ready` (`ready`), api-spec.md §9.1–§9.2. */
@Serializable
data class StatusResponse(val status: String) {
    companion object {
        val OK: StatusResponse = StatusResponse("ok")
        val READY: StatusResponse = StatusResponse("ready")
    }
}
