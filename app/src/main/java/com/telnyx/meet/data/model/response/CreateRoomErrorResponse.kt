/*
 * Copyright © 2021 Telnyx LLC. All rights reserved.
 */

package com.telnyx.meet.data.model.response

import com.google.gson.annotations.SerializedName
data class CreateRoomErrorResponse(
    @SerializedName("errors")
    val error: RoomError
)

class RoomError(
    @SerializedName("unique_name_not_unique_for_user_id")
    val nameNotUnique: List<String>
)
