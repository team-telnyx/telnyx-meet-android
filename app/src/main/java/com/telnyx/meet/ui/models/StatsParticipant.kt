package com.telnyx.meet.ui.models

data class StatsParticipant(
    val participantId: String,
    val isSelf: Boolean,
    val isPresentation: Boolean
)
