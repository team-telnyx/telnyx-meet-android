/*
 * Copyright © 2021 Telnyx LLC. All rights reserved.
 */

package com.telnyx.meet.data.model.request

data class CreateRoomRequest(val max_participant: Int = 10, val unique_name: String)
