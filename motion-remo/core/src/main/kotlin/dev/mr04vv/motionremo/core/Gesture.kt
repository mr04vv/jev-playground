package dev.mr04vv.motionremo.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface RemoAction {
    val applianceId: String
    val applianceName: String

    @Serializable
    @SerialName("light")
    data class Light(override val applianceId: String, override val applianceName: String, val button: String) : RemoAction

    @Serializable
    @SerialName("aircon")
    data class Aircon(override val applianceId: String, override val applianceName: String, val on: Boolean) : RemoAction

    /** Registered for motions like picking the phone up, so they match something harmless instead of a real action. */
    @Serializable
    @SerialName("ignore")
    data object Ignore : RemoAction {
        override val applianceId: String get() = ""
        override val applianceName: String get() = ""
    }
}

@Serializable
data class RegisteredGesture(
    val id: String,
    val name: String,
    val action: RemoAction,
    val samples: List<MotionFeatures>,
)
