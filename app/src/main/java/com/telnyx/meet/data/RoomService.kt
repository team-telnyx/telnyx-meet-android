package com.telnyx.meet.data

import com.telnyx.meet.data.model.response.CreateRoomResponse
import com.telnyx.meet.data.model.response.RefreshTokenResponse
import com.telnyx.meet.data.model.response.RoomsListResponse
import com.telnyx.meet.data.model.request.CreateRoomRequest
import com.telnyx.meet.data.model.request.CreateTokenRequest
import com.telnyx.meet.data.model.request.RefreshTokenRequest
import com.telnyx.meet.data.model.response.CreateTokenResponse
import retrofit2.Call
import retrofit2.http.*

interface RoomService {

    @Headers(
        "Content-Type: application/json",
        "Accept: application/json"
    )
    @GET("rooms?")
    fun getRoomList(
        @Query("page[number]") page: Int
    ): Call<RoomsListResponse>

    @Headers(
        "Content-Type: application/json",
        "Accept: application/json"
    )
    @POST("rooms")
    fun createRoom(@Body roomRequest: CreateRoomRequest): Call<CreateRoomResponse>

    @Headers(
        "Content-Type: application/json",
        "Accept: application/json"
    )
    @DELETE("rooms/{room_id}")
    fun deleteRoom(@Path("room_id") roomId: String): Call<Void>

    @Headers(
        "Content-Type: application/json",
        "Accept: application/json"
    )
    @POST("rooms/{room_id}/actions/generate_join_client_token")
    fun createClientToken(
        @Path("room_id") roomId: String,
        @Body roomRequest: CreateTokenRequest
    ): Call<CreateTokenResponse>

    @POST("rooms/{room_id}/actions/refresh_client_token")
    fun refreshClientToken(
        @Path("room_id") roomId: String,
        @Body roomRequest: RefreshTokenRequest
    ): Call<RefreshTokenResponse>
}
