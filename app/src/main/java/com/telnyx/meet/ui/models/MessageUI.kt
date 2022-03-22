package com.telnyx.meet.ui.models

import com.telnyx.video.sdk.webSocket.model.ui.Message

data class MessageUI(
    val sender: String,
    val fullMessage: Message,
    var date: String
)
