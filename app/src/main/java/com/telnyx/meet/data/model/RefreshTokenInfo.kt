/*
 * Copyright © 2021 Telnyx LLC. All rights reserved.
 */

package com.telnyx.meet.data.model

data class RefreshTokenInfo(
    val token: String,
    val token_expires_at: String
)
