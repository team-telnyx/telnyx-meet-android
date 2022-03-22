package com.telnyx.meet.di

import androidx.fragment.app.Fragment
import com.telnyx.meet.ui.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
abstract class FragmentFactoryModule {

    @Binds
    @IntoMap
    @FragmentKey(JoinRoomFragment::class)
    abstract fun bindJoinRoomFragment(fragment: JoinRoomFragment): Fragment

    @Binds
    @IntoMap
    @FragmentKey(RoomCreateFragment::class)
    abstract fun bindRoomCreateFragment(fragment: RoomCreateFragment): Fragment

    @Binds
    @IntoMap
    @FragmentKey(RoomFragment::class)
    abstract fun bindRoomFragment(fragment: RoomFragment): Fragment

    @Binds
    @IntoMap
    @FragmentKey(RoomChatFragment::class)
    abstract fun bindRoomChatFragment(fragment: RoomChatFragment): Fragment

    @Binds
    @IntoMap
    @FragmentKey(RoomListFragment::class)
    abstract fun bindRoomListFragment(fragment: RoomListFragment): Fragment

    @Binds
    @IntoMap
    @FragmentKey(SharingFullScreenFragment::class)
    abstract fun bindSharingFullScreenFragment(fragment: SharingFullScreenFragment): Fragment
}
