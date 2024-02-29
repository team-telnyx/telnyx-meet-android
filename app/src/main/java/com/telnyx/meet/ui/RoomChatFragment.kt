package com.telnyx.meet.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.telnyx.meet.BaseFragment
import com.telnyx.meet.R
import com.telnyx.meet.databinding.ChatDialogLayoutBinding
import com.telnyx.meet.navigator.Navigator
import com.telnyx.meet.ui.adapters.MessageListAdapter
import com.telnyx.meet.ui.models.MessageUI
import com.telnyx.meet.ui.models.SelectedParticipant
import com.telnyx.meet.ui.utilities.getCurrentTimeHHmm
import com.telnyx.meet.ui.utilities.hideKeyboard
import com.telnyx.video.sdk.webSocket.model.ui.Message
import com.telnyx.video.sdk.webSocket.model.ui.MessageContent
import com.telnyx.video.sdk.webSocket.model.ui.MessageType
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class RoomChatFragment @Inject constructor(
    val navigator: Navigator
) : BaseFragment<ChatDialogLayoutBinding>() {

    val roomsViewModel: RoomsViewModel by activityViewModels()
    private var participantsInChat = mutableListOf<SelectedParticipant>()

    override val layoutId: Int = R.layout.chat_dialog_layout
    override fun inflate(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): ChatDialogLayoutBinding {
        return ChatDialogLayoutBinding.inflate(inflater, container, false)
    }

    private var roomChatAdapter: MessageListAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            chatSendBtn.setOnClickListener {
                sendUserMessage()
                chatEditText.text.clear()
                chatEditText.clearFocus()
                hideKeyboard()
            }

            selectParticipantsButton.setOnClickListener {
                val builder = AlertDialog.Builder(context)
                val namesArray =
                    participantsInChat.map { it.participant.externalUsername }.toTypedArray()
                val selectedArray = participantsInChat.map { it.selected }.toBooleanArray()

                builder.setMultiChoiceItems(namesArray, selectedArray) { dialog, which, isChecked ->
                    // Update the current focused item's checked status
                    selectedArray[which] = isChecked
                }
                builder.setPositiveButton("OK") { _, _ ->
                    // Do something when click positive button
                    selectedArray.forEachIndexed { index, isSelected ->
                        participantsInChat[index].selected = isSelected
                    }
                }
                builder.setNeutralButton("Clear") { _, _ ->
                    participantsInChat.forEach {
                        it.selected = false
                    }
                }
                val dialog = builder.create()
                dialog.show()
            }
        }

    }

    private fun sendUserMessage() {
        val messageText = binding.chatEditText.text.toString()
        val messageContent = MessageContent(type = MessageType.TEXT.type, messageText, null)
        val usersToArray =
            participantsInChat.filter { it.selected }.map { it.participant.participantId }.toList()
        val message = Message(messageContent, usersToArray)
        roomsViewModel.sendTextRoomMessage(message)
    }

    override fun onResume() {
        super.onResume()
        setAdapter()
        setObservers()
    }

    private fun setAdapter() {
        roomChatAdapter =
            MessageListAdapter(
                requireContext(),
                roomsViewModel.getChatHistory()
            )
        binding.chatRecyclerLayout.adapter = roomChatAdapter
        roomChatAdapter?.let {
            val lastPosition = it.itemCount - 1
            if (lastPosition >= 0) {
                binding.chatRecyclerLayout.layoutManager =
                    LinearLayoutManager(context).apply {
                        scrollToPositionWithOffset(lastPosition, 0)
                    }
            }
        }
    }

    private fun setObservers() {
        roomsViewModel.getParticipants()
            .observe(this.viewLifecycleOwner) { textRoomParticipants ->
                textRoomParticipants.forEach { participant ->
                    if (participant.canReceiveMessages) {
                        roomChatAdapter?.addParticipant(participant)
                        if (!participant.isSelf) {
                            participantsInChat.add(
                                SelectedParticipant(participant = participant)
                            )
                        }
                    }
                }
            }

        roomsViewModel.getJoinedParticipant()
            .observe(this.viewLifecycleOwner) { joinedParticipantEvent ->
                joinedParticipantEvent.getContentIfNotHandled()?.let { participant ->
                    if (participant.canReceiveMessages) {
                        roomChatAdapter?.addParticipant(participant)
                        participantsInChat.add(SelectedParticipant(participant = participant))
                        saveAdminMessage("${participant.externalUsername} has joined")
                    }
                }
            }

        roomsViewModel.getTextRoomLeavingParticipant()
            .observe(this.viewLifecycleOwner) { participantEvent ->
                participantEvent.getContentIfNotHandled()?.let { participant ->
                    participantsInChat.find { it.participant.participantId == participant.participantId }
                        ?.let {
                            participantsInChat.remove(it)
                        }
                    saveAdminMessage("${participant.externalUsername} has left")
                }
            }

        roomsViewModel.getTextRoomMessageDeliverySuccess()
            .observe(this.viewLifecycleOwner) { messageDeliveredEvent ->
                messageDeliveredEvent.getContentIfNotHandled()?.let {
                    val messageUI =
                        MessageUI(sender = "SELF", fullMessage = it, getCurrentTimeHHmm())
                    roomChatAdapter?.addMessageToChat(messageUI)
                    roomChatAdapter?.let { adapter ->
                        binding.chatRecyclerLayout.scrollToPosition(adapter.itemCount - 1)
                    }
                }
            }

        roomsViewModel.getTextRoomMessageDeliveryFailed()
            .observe(this.viewLifecycleOwner) { messageNotDeliveredEvent ->
                messageNotDeliveredEvent.getContentIfNotHandled()?.let {
                    Timber.e("Message not delivered: $it")
                }
            }

        roomsViewModel.getTextRoomMessageReceived()
            .observe(this.viewLifecycleOwner) { messageReceivedEventPair ->
                messageReceivedEventPair.getContentIfNotHandled()
                    ?.let { (participantId, fullMessage) ->
                        val messageUI =
                            MessageUI(
                                sender = participantId,
                                fullMessage = fullMessage,
                                getCurrentTimeHHmm()
                            )
                        roomChatAdapter?.addMessageToChat(messageUI)
                        roomChatAdapter?.let { adapter ->
                            binding.chatRecyclerLayout.scrollToPosition(adapter.itemCount - 1)
                        }
                    }
            }
    }

    private fun saveAdminMessage(adminMessageText: String) {
        roomChatAdapter?.addMessageToChat(
            roomsViewModel.createAdminMessage(adminMessageText)
        )
        roomChatAdapter?.let { adapter ->
            binding.chatRecyclerLayout.scrollToPosition(adapter.itemCount - 1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        roomsViewModel.saveChatHistory(roomChatAdapter?.getMessages())
    }
}
