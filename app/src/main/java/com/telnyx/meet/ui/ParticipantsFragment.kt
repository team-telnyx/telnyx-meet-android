package com.telnyx.meet.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.telnyx.meet.BaseFragment
import com.telnyx.meet.R
import com.telnyx.meet.databinding.ParticipantsFragmentBinding
import com.telnyx.meet.ui.adapters.ParticipantsAdapter

class ParticipantsFragment : BaseFragment<ParticipantsFragmentBinding>() {

    val roomsViewModel: RoomsViewModel by activityViewModels()

    private var participantsAdapter = ParticipantsAdapter()
    override val layoutId: Int
        get() = R.layout.participants_fragment

    override fun inflate(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): ParticipantsFragmentBinding {
        return ParticipantsFragmentBinding.inflate(inflater, container, false)
    }

    private fun setObservers() {
        roomsViewModel.getParticipants().observe(viewLifecycleOwner) { participants ->
            participants.let { participantList ->
                participantsAdapter.setData(participantList)
            }
        }

        roomsViewModel.getJoinedParticipant().observe(viewLifecycleOwner) { participantJoined ->
            participantJoined?.let { joinedParticipantEvent ->
                joinedParticipantEvent.getContentIfNotHandled()?.let {
                    participantsAdapter.addParticipant(it)
                }
            }
        }

        roomsViewModel.getLeavingParticipantId()
            .observe(viewLifecycleOwner) { participantLeavingId ->
                participantLeavingId?.let {
                    participantsAdapter.removeParticipant(it.first)
                }
            }

        roomsViewModel.getSpeakingParticipant().observe(viewLifecycleOwner) { speakingParticipant ->
            speakingParticipant?.let {
                participantsAdapter.notifyDataSetChanged()
            }
        }

        roomsViewModel.getParticipantStreamChanged()
            .observe(viewLifecycleOwner) { participantChangedStreamsEvent ->
                participantChangedStreamsEvent.getContentIfNotHandled()?.let {
                    participantsAdapter.notifyDataSetChanged()
                }
            }
    }

    override fun onResume() {
        super.onResume()
        setupRecyclerView()
        setObservers()
    }

    private fun setupRecyclerView() {
        binding.apply {
            participantRecycler.adapter = participantsAdapter
            val linearLayoutManager = LinearLayoutManager(context)
            participantRecycler.layoutManager = linearLayoutManager
        }

    }
}
