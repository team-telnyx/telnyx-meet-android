package com.telnyx.meet.ui.models

data class StatsParticipant(
    val participantId: String,
    val isSelf: Boolean,
    val isPresentation: Boolean
)

enum class StatsSource(val source: String) {
    REMOTE_VIDEO("remote_video"),
    REMOTE_AUDIO("remote_audio"),
    LOCAL_VIDEO("local_video"),
    LOCAL_AUDIO("local_audio")
}
