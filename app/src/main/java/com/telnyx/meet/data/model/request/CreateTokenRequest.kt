/*
 * Copyright © 2021 Telnyx LLC. All rights reserved.
 */

package com.telnyx.meet.data.model.request

data class CreateTokenRequest(val refresh_token_ttl_secs: Int = 3600, val token_ttl_secs: Int = 600)
