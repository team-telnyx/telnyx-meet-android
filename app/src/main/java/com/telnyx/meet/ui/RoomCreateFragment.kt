package com.telnyx.meet.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import com.telnyx.meet.BaseFragment
import com.telnyx.meet.R
import com.telnyx.meet.databinding.RoomCreateFragmentBinding
import com.telnyx.meet.navigator.Navigator
import com.telnyx.meet.ui.utilities.calculateTokenExpireTime
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class RoomCreateFragment @Inject constructor(
    val navigator: Navigator
) : BaseFragment<RoomCreateFragmentBinding>() {

    private lateinit var mParticipantName: String
    private var mRoomId: String? = null
    private var mRoomName: String? = null
    private var mRefreshToken: String? = null
    private var mRefreshTimer: Int? = null
    val roomsViewModel: RoomsViewModel by activityViewModels()
    override val layoutId: Int = R.layout.room_create_fragment
    override fun inflate(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): RoomCreateFragmentBinding {
        return RoomCreateFragmentBinding.inflate(inflater, container, false)
    }

    private val args: com.telnyx.meet.ui.RoomCreateFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            roomNameEt.addTextChangedListener {
                mRoomName = it.toString()
            }

            if (args.roomName != null) {
                createRoomFields.visibility = View.GONE
                roomloadingProgressBar.visibility = View.VISIBLE
            } else {
                roomloadingProgressBar.visibility = View.GONE
            }

            buttonCreateRoom.setOnClickListener {
                Timber.tag("RoomCreateFragment").d("mRoomName is currently :: $mRoomName")
                if (mRoomName !== null) {
                    roomsViewModel.createNewRoom(mRoomName.toString())
                } else {
                    roomNameIly.error = getString(R.string.empty_or_not_unique_error)
                }
            }
        }

    }

    override fun onResume() {
        super.onResume()
        clearInfo()
        setArgs()
        setObservers()
        initiateRoomCreateSequence()
    }

    private fun setArgs() {
        mParticipantName = args.participantName
        mRoomId = args.roomId
        mRoomName = args.roomName
    }

    private fun setObservers() {

        binding.apply{}
        roomsViewModel.roomCreatedObservable().observe(this.viewLifecycleOwner) {
            it?.let {
                mRoomId = it.id
                mRoomName = it.unique_name
                binding.tvRoomId.text = mRoomId
                binding.tvRoomName.text = mRoomName
                roomsViewModel.createTokenForRoom(it.id, mParticipantName)
            }
        }

        roomsViewModel.tokenCreatedObservable()
            .observe(this.viewLifecycleOwner) { tokenCreatedEvent ->
                tokenCreatedEvent.getContentIfNotHandled()?.let { tokenInfo ->
                    mRefreshTimer = calculateTokenExpireTime(tokenInfo.token_expires_at)
                    Timber.tag("RoomCreateFragment").d("Refresh timer set to: $mRefreshTimer")
                    mRefreshToken = tokenInfo.refresh_token
                    binding.tvRoomToken.text = tokenInfo.token
                    mRoomId?.let {
                        setRoomSpecificObservers()
                        roomsViewModel.connectToRoom()
                    }
                }
            }
    }

    private fun setRoomSpecificObservers() {
        roomsViewModel.connectedToRoomObservable().observe(this.viewLifecycleOwner) {
            it?.let { isConnected ->
                if (isConnected) {
                    binding.buttonCreateRoom.isEnabled = true
                }
            }
        }

        roomsViewModel.loading().observe(this) { isLoading ->
            if (isLoading) {
                binding.createRoomProgressBar.visibility = View.VISIBLE
            } else {
               binding.createRoomProgressBar.visibility = View.GONE
            }
        }

        roomsViewModel.error().observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { errorResponse ->
                errorResponse.error.let {
                    binding.buttonCreateRoom.isEnabled = true
                    Toast.makeText(
                        context,
                        "Room wasn't created: ${it.nameNotUnique[0]}",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.roomNameIly.error = getString(R.string.empty_or_not_unique_error)
                }
            }
        }

        roomsViewModel.tokenCreationErrorObservable()
            .observe(viewLifecycleOwner) { tokenErrorBoolean ->
                if (tokenErrorBoolean) {
                    Toast.makeText(
                        context,
                        "Invalid room ID",
                        Toast.LENGTH_LONG
                    ).show()
                    val action =
                        com.telnyx.meet.ui.RoomCreateFragmentDirections.roomCreateToRoomToJoinRoomFragment()
                    navigator.navController.navigate(action)
                }
            }

        roomsViewModel.getJoinedRoom().observe(viewLifecycleOwner) { joinedRoomEvent ->
            joinedRoomEvent.getContentIfNotHandled()?.let { joinedRoom ->
                if (joinedRoom.joined) {
                    navigateToRoom()
                }
            }
        }
    }

    private fun navigateToRoom() {
        if (mRoomId != null && mRoomName != null && mRefreshToken != null && mRefreshTimer != null) {
            val action = com.telnyx.meet.ui.RoomCreateFragmentDirections.roomCreateToRoom(
                mRoomId!!,
                mRoomName!!,
                mRefreshToken!!,
                mRefreshTimer!!
            )
            navigator.navController.navigate(action)
        }
    }

    private fun initiateRoomCreateSequence() {
        clearInfo()
        mRoomId?.let { roomId ->
            binding.apply {
                tvRoomId.text = mRoomId
                mRoomName?.let { _ ->
                    tvRoomName.text = mRoomName
                    roomNameEt.setText(mRoomName)
                } ?: run {
                    showCreateRoomFields()
                }
                roomsViewModel.createTokenForRoom(roomId, mParticipantName)
                buttonCreateRoom.isEnabled = true
                roomNameEt.addTextChangedListener {
                    buttonCreateRoom.isEnabled = true
                    mRoomName = it.toString()
                }
            }

        } ?: run {
            showCreateRoomFields()
        }
    }

    override fun onStop() {
        super.onStop()
        roomsViewModel.stopObserveRoomCreatedValue()
    }

    private fun showCreateRoomFields() {
        binding.apply {
            createRoomFields.visibility = View.VISIBLE
            roomloadingProgressBar.visibility = View.GONE
        }
    }

    private fun clearInfo() {
        binding.apply {
            tvRoomName.text = "..."
            tvRoomId.text = "..."
            tvRoomToken.text = "..."
        }

    }
}
