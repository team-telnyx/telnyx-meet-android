package com.telnyx.meet.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.telnyx.meet.R
import com.telnyx.video.sdk.webSocket.model.ui.Participant
import com.telnyx.video.sdk.webSocket.model.ui.StreamStatus
import kotlinx.android.synthetic.main.participant_tile_item.view.*
import org.webrtc.SurfaceViewRenderer
import timber.log.Timber

interface ParticipantTileListener {
    fun onItemClicked(model: Participant)
    fun notifyTileSelfSurfaceId(participantSurface: SurfaceViewRenderer)
    fun notifyTileSurfaceId(
        participantSurface: SurfaceViewRenderer,
        participantId: String,
        streamKey: String
    )

    fun unsubscribeTileToStream(participantId: String, streamKey: String)
    fun subscribeTileToStream(participantId: String, streamKey: String)
    fun notifyExtraParticipants(extraParticipants: Int)
}

class ParticipantTileAdapter(private val participantTileListener: ParticipantTileListener) :
    RecyclerView.Adapter<ParticipantTileAdapter.ParticipantTileHolder>() {

    private val participants = mutableListOf<Participant>()
    private val participantsInAdapter = mutableListOf<Participant>()
    private var maxParticipants: Int = 8

    fun setData(data: List<Participant>) {
        participants.clear()
        participants.addAll(data)
        calculateParticipants()
    }

    private fun calculateParticipants() {
        participantsInAdapter.clear()
        participantsInAdapter.addAll(participants.take(maxParticipants))
        val remainingParticipants = participants.size - participantsInAdapter.size
        if (remainingParticipants > 0) {
            pauseRemainingParticipants(participants.takeLast(remainingParticipants))
        }
        participantTileListener.notifyExtraParticipants(remainingParticipants)
        notifyDataSetChanged()
    }

    private fun pauseRemainingParticipants(participantsNotVisible: List<Participant>) {
        participantsNotVisible.forEach {
            participantTileListener.unsubscribeTileToStream(it.participantId, "self")
        }
    }

    fun getPositionFromParticipant(participant: Participant): Int {
        return participantsInAdapter.indexOf(participant)
    }

    fun addParticipant(participant: Participant) {
        var modified = false
        participants.find { it.participantId == participant.participantId }?.let {
            it.id = participant.id
            it.externalUsername = participant.externalUsername
            it.streams = participant.streams
            it.audioBridgeId = participant.audioBridgeId
            it.canReceiveMessages = participant.canReceiveMessages
        } ?: run {
            Timber.tag("ParticipantTileAdapter")
                .d("Adding participant: ${participant.participantId}")
            participants.add(participant)
            modified = true
        }
        if (modified) calculateParticipants()
    }

    fun removeParticipant(leavingId: Long) {
        var modified = false
        participants.find { it.id == leavingId }?.let {
            Timber.tag("ParticipantTileAdapter")
                .d("Removing participant: $leavingId ${it.participantId}")
            participants.remove(it)
            modified = true
        }
        if (modified) calculateParticipants()
    }

    fun adjustSpeakingParticipants(list: List<Participant>) {
        Timber.tag("ParticipantTileAdapter").d("Updating participants speaking: $list")
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long {
        return participantsInAdapter[position].id
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ParticipantTileHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ParticipantTileHolder(
            inflater.inflate(
                R.layout.participant_tile_item,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int = participantsInAdapter.size

    override fun onBindViewHolder(holder: ParticipantTileHolder, position: Int) {
        holder.bind(participantsInAdapter[position], participantTileListener)
    }

    class ParticipantTileHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bind(model: Participant, participantTileListener: ParticipantTileListener) {
            Timber.tag("ParticipantTileAdapter").d("onBind() isSelf: ${model.isSelf}")
            itemView.participant_tile_id.text =
                model.participantId.substring(0, 5)
            itemView.participant_tile_name.text = model.externalUsername.toString()

            Timber.tag("ParticipantTileAdapter")
                .d("onBind() talkingUpdate ${model.externalUsername} ${model.isTalking}")

            if (model.isAudioCensored == true) {
                itemView.isSpeakingIcon_id.setImageResource(R.drawable.ic_speaker_attendee_off)
                itemView.isSpeakingIcon_id.visibility = View.VISIBLE
            } else {
                itemView.isSpeakingIcon_id.setImageResource(R.drawable.ic_campaign_white_dp)
                if (model.isTalking == "talking") {
                    itemView.isSpeakingIcon_id.visibility = View.VISIBLE
                } else {
                    itemView.isSpeakingIcon_id.visibility = View.INVISIBLE
                }
            }

            if (model.isSelf) participantTileListener.notifyTileSelfSurfaceId(itemView.participant_tile_surface)

            // TODO review this logic: will only subscribe to audio, if video is enabled as well
            when (model.streams.find { it.streamKey == "self" }?.videoEnabled) {
                StreamStatus.ENABLED -> {
                    Timber.tag("ParticipantTileAdapter").d("onBind() STARTED")
                    participantTileListener.subscribeTileToStream(model.participantId, "self")
                    itemView.participant_tile_surface.visibility = View.VISIBLE
                    itemView.participant_tile_place_holder.visibility = View.GONE
                    model.streams.find { it.streamKey == "self" }?.videoTrack?.addSink(itemView.participant_tile_surface)
                    model.streams.find { it.streamKey == "self" }?.videoTrack?.setEnabled(true)
                    participantTileListener.notifyTileSurfaceId(
                        itemView.participant_tile_surface,
                        model.participantId,
                        "self"
                    )
                }
                else -> {
                    Timber.tag("ParticipantTileAdapter").d("onBind() PAUSED")
                    participantTileListener.unsubscribeTileToStream(model.participantId, "self")
                    itemView.participant_tile_surface.visibility = View.INVISIBLE
                    itemView.participant_tile_place_holder.visibility = View.VISIBLE
                }
            }
/*
            when (model.streams.find { it.streamKey == "self" }?.audioEnabled) {
            }
                StreamStatus.ENABLED -> {
                    participantTileListener.subscribeTileToStream(model.participantId)
                }
                else -> {
                    Timber.tag("ParticipantTileAdapter").d("onBind() Do nothing")
                }
*/
            itemView.setOnClickListener {
                participantTileListener.onItemClicked(model)
            }
        }
    }

    fun setMaxParticipants(maxParticipants: Int) {
        this.maxParticipants = maxParticipants
        calculateParticipants()
    }
}
