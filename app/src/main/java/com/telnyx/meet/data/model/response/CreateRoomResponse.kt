/*
 * Copyright © 2021 Telnyx LLC. All rights reserved.
 */

package com.telnyx.meet.data.model.response

import com.google.gson.annotations.SerializedName
import com.telnyx.meet.data.model.RoomDetails

data class CreateRoomResponse(
    @SerializedName("data")
    val roomDetails: RoomDetails
)

data class CreateAudioBridgeRoomResponse(
    val audiobridge: String,
    val room: String,
    val permanent: Boolean
)
