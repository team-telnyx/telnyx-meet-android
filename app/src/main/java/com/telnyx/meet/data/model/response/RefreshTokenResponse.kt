/*
 * Copyright © 2021 Telnyx LLC. All rights reserved.
 */

package com.telnyx.meet.data.model.response

import com.google.gson.annotations.SerializedName
import com.telnyx.meet.data.model.RefreshTokenInfo

data class RefreshTokenResponse(
    @SerializedName("data")
    val refreshTokenInfo: RefreshTokenInfo
)
