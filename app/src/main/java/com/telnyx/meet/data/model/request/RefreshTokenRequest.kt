/*
 * Copyright © 2021 Telnyx LLC. All rights reserved.
 */

package com.telnyx.meet.data.model.request

data class RefreshTokenRequest(val refresh_token: String, val token_ttl_secs: Int = 600)
