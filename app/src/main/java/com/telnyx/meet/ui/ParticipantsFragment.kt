package com.telnyx.meet.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.telnyx.meet.R
import com.telnyx.meet.ui.adapters.ParticipantsAdapter
import kotlinx.android.synthetic.main.participants_fragment.*

class ParticipantsFragment : Fragment() {

    val roomsViewModel: RoomsViewModel by activityViewModels()

    private var participantsAdapter = ParticipantsAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.participants_fragment, container, false)
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
            .observe(viewLifecycleOwner) { participantChangedStreams ->
                participantChangedStreams?.let {
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
        participantRecycler.adapter = participantsAdapter
        val linearLayoutManager = LinearLayoutManager(context)
        participantRecycler.layoutManager = linearLayoutManager
    }
}
