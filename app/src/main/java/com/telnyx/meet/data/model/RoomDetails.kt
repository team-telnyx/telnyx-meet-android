/*
 * Copyright © 2021 Telnyx LLC. All rights reserved.
 */

package com.telnyx.meet.data.model

data class RoomDetails(
    val created_at: String,
    val id: String,
    val max_participants: Int,
    val record_type: String,
    var unique_name: String,
    val updated_at: String
)
