/*
 * Copyright © 2021 Telnyx LLC. All rights reserved.
 */

package com.telnyx.meet.data.model.response

import com.google.gson.annotations.SerializedName
import com.telnyx.meet.data.model.RoomDetails
import com.telnyx.meet.data.model.RoomListMetadata

data class RoomsListResponse(

    @SerializedName("data")
    val roomDetails: List<RoomDetails>,

    val meta: RoomListMetadata
)
