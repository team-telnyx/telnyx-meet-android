package com.telnyx.meet.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.telnyx.meet.R
import com.telnyx.meet.databinding.ParticipantItemBinding
import com.telnyx.meet.ui.RoomFragment
import com.telnyx.video.sdk.webSocket.model.ui.Participant
import com.telnyx.video.sdk.webSocket.model.ui.StreamStatus
import timber.log.Timber

class ParticipantsAdapter : RecyclerView.Adapter<ParticipantsAdapter.ParticipantHolder>() {
    private val participants = mutableListOf<Participant>()

    fun setData(data: List<Participant>) {
        participants.clear()
        participants.addAll(data)
        this.notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ParticipantHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ParticipantHolder(
            inflater.inflate(
                R.layout.participant_item,
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ParticipantHolder, position: Int) {
        holder.bind(participants[position])
    }

    override fun getItemCount(): Int {
        return participants.size
    }

    class ParticipantHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val binding = ParticipantItemBinding.bind(itemView)
        fun bind(model: Participant) {
            binding.apply {
                 participantNameTv.text =
                    model.externalUsername

                if (model.isTalking == "talking") {
                    isSpeakingIconId.visibility = View.VISIBLE
                } else {
                    isSpeakingIconId.visibility = View.INVISIBLE
                }

                if (model.streams.find { it.streamKey == RoomFragment.RoomFragmentConstants.SELF_STREAM_KEY }?.audioEnabled == StreamStatus.ENABLED) {
                    isMutedIconId.setImageResource(R.drawable.ic_mic)
                } else {
                    isMutedIconId.setImageResource(R.drawable.ic_mic_off)
                }

                if (model.streams.find { it.streamKey == "self" }?.videoEnabled == StreamStatus.ENABLED) {
                    isVideoIconId.setImageResource(R.drawable.ic_camera)
                } else {
                    isVideoIconId.setImageResource(R.drawable.ic_camera_off)
                }
            }

            itemView.setOnClickListener {
                // We need to decide functionality here. Perhaps navigate to their surface and pin it?
            }
        }
    }

    fun removeParticipant(leavingId: Long) {
        participants.find { it.id == leavingId }?.let {
            Timber.tag("ParticipantsAdapter")
                .d("Removing participant: $leavingId ${it.participantId}")
            participants.remove(it)
        }
        this.notifyDataSetChanged()
    }

    fun adjustSpeakingParticipants(list: List<Participant>) {
        Timber.tag("ParticipantsAdapter").d("Updating participants speaking: $list")
        this.notifyDataSetChanged()
    }

    fun addParticipant(participant: Participant) {
        participants.find { it.participantId == participant.participantId }?.let {
            it.externalUsername = participant.externalUsername
            it.id = participant.id
        } ?: run {
            Timber.tag("ParticipantsAdapter").d("Adding participant: ${participant.participantId}")
            participants.add(participant)
        }
        this.notifyDataSetChanged()
    }
}
