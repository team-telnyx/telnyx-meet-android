/*
 * Copyright © 2021 Telnyx LLC. All rights reserved.
 */

package com.telnyx.meet.data.model

data class RoomListMetadata(
    val page_number: Int,
    val page_size: Int,
    val total_pages: Int,
    val total_results: Int
)
