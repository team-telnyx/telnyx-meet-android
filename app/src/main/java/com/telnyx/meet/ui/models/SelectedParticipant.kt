package com.telnyx.meet.ui.models

import com.telnyx.video.sdk.webSocket.model.ui.Participant

data class SelectedParticipant(
    val participant: Participant,
    var selected: Boolean = false
)
