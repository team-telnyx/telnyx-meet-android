/*
 * Copyright © 2021 Telnyx LLC. All rights reserved.
 */

package com.telnyx.meet.data.model

data class TokenInfo(
    val recort_type: String,
    val refresh_token: String,
    val refresh_token_expires_at: String,
    val token: String,
    val token_expires_at: String
)
