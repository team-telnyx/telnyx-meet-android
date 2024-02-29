package com.telnyx.meet.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.telnyx.meet.BaseFragment
import com.telnyx.meet.R
import com.telnyx.meet.databinding.RoomListFragmentBinding
import com.telnyx.meet.navigator.Navigator
import com.telnyx.meet.ui.adapters.ClickListener
import com.telnyx.meet.ui.adapters.RoomAdapter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RoomListFragment @Inject constructor(
    val navigator: Navigator
) : BaseFragment<RoomListFragmentBinding>(), ClickListener {

    private var participantName: String = ""
    val roomsViewModel: RoomsViewModel by activityViewModels()

    override val layoutId: Int = R.layout.room_list_fragment
    override fun inflate(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): RoomListFragmentBinding {
        return RoomListFragmentBinding.inflate(inflater, container, false)
    }

    private var roomListAdapter: RoomAdapter? = null

    private var permissionsGranted = false

    private val args: com.telnyx.meet.ui.RoomListFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        participantName = args.participantName
    }

    override fun onItemClicked(roomId: String, roomName: String) {
        if (permissionsGranted) {
            navigateToRoomCreate(roomId, roomName, true)
        } else {
            roomsViewModel.checkPermissions(requireActivity())
        }
    }

    override fun snackBarDismissed(roomId: String, roomName: String) {
        roomsViewModel.deleteRoom(roomId)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            participantNameEt?.setText(args.participantName)
            participantNameEt.addTextChangedListener {
                participantName = it.toString()
                participantNameIt.error = ""
            }

            setupRecyclerView()

            setObservers()

            roomsViewModel.checkPermissions(requireActivity())
            roomsViewModel.getRoomList()

            fab.setOnClickListener {
                navigateToRoomCreate(isForJoin = false)
            }
        }

    }

    private fun setObservers() {
        roomsViewModel.roomListObservable().observe(this.viewLifecycleOwner) { roomList ->
            roomList?.let {
                if (roomsViewModel.getLastRetrievedPage() > 1) {
                    roomListAdapter?.addData(roomList)
                } else {
                    roomListAdapter?.setData(roomList)
                }
            }
        }

        roomsViewModel.permissionRequest.observe(this.viewLifecycleOwner) { permission ->
            permissionsGranted = permission
        }

        roomsViewModel.deleted().observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { deletedEvent ->
                if (deletedEvent) {
                    roomListAdapter?.notifyDataSetChanged()
                }
            }
        }
    }

    private fun navigateToRoomCreate(
        roomId: String? = null,
        roomName: String? = null,
        isForJoin: Boolean
    ) {
        if (participantName.isEmpty()) {
            participantName = binding.participantNameEt.text.toString()
        }
        participantName.let { participantName ->
            if (participantName.isNotEmpty()) {
                if (!roomId.isNullOrEmpty() || !isForJoin) {
                    val action =
                        com.telnyx.meet.ui.RoomListFragmentDirections.roomListToRoomCreate(
                            participantName
                        )
                    action.roomId = roomId
                    if (roomName != null) {
                        action.roomName = roomName
                    }
                    navigator.navController.navigate(action)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Unable to navigate to room",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            } else {
               binding.participantNameIt.error = getString(R.string.provide_participant_name_error)
            }
        }
    }

    private fun setupRecyclerView() {
        binding.apply {
            roomListAdapter = RoomAdapter(this@RoomListFragment)
            roomsRecycler.adapter = roomListAdapter
            val linearLayoutManager = LinearLayoutManager(context)
            roomsRecycler.layoutManager = linearLayoutManager

            // Infinite scroller
            var previousTotal = 0
            val visibleThreshold = 5
            var visibleItemCount: Int
            var totalItemCount: Int
            var pastVisiblesItems: Int
            var loading = true
            roomsRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy > 0) { // check for scroll down
                        visibleItemCount = linearLayoutManager.childCount
                        totalItemCount = linearLayoutManager.itemCount
                        pastVisiblesItems = linearLayoutManager.findFirstVisibleItemPosition()
                        if (loading) {
                            if (totalItemCount > previousTotal) {
                                loading = false
                                previousTotal = totalItemCount
                            }
                        }
                        if (!loading && (totalItemCount - visibleItemCount)
                            <= (pastVisiblesItems + visibleThreshold)
                        ) {
                            loading = false
                            roomsViewModel.getMoreRooms()
                            loading = true
                        }
                    }
                }
            })
            val deleteIcon =
                context?.let { ContextCompat.getDrawable(it, R.drawable.ic_delete_white_24) }!!
            val colorDrawableBackground = ColorDrawable(Color.parseColor("#ff0000"))
            val itemTouchHelperCallback =
                object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
                    override fun onMove(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                        target: RecyclerView.ViewHolder
                    ): Boolean {
                        return false
                    }

                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDirection: Int) {
                        (roomListAdapter as RoomAdapter).removeItem(
                            viewHolder.adapterPosition,
                            viewHolder
                        )
                    }

                    override fun onChildDraw(
                        c: Canvas,
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                        dX: Float,
                        dY: Float,
                        actionState: Int,
                        isCurrentlyActive: Boolean
                    ) {
                        val itemView = viewHolder.itemView
                        val iconMarginVertical =
                            (viewHolder.itemView.height - deleteIcon.intrinsicHeight) / 2

                        if (dX > 0) {
                            colorDrawableBackground.setBounds(
                                itemView.left,
                                itemView.top,
                                dX.toInt(),
                                itemView.bottom
                            )
                            deleteIcon.setBounds(
                                itemView.left + iconMarginVertical,
                                itemView.top + iconMarginVertical,
                                itemView.left + iconMarginVertical + deleteIcon.intrinsicWidth,
                                itemView.bottom - iconMarginVertical
                            )
                        } else {
                            colorDrawableBackground.setBounds(
                                itemView.right + dX.toInt(),
                                itemView.top,
                                itemView.right,
                                itemView.bottom
                            )
                            deleteIcon.setBounds(
                                itemView.right - iconMarginVertical - deleteIcon.intrinsicWidth,
                                itemView.top + iconMarginVertical,
                                itemView.right - iconMarginVertical,
                                itemView.bottom - iconMarginVertical
                            )
                            deleteIcon.level = 0
                        }

                        colorDrawableBackground.draw(c)

                        c.save()

                        if (dX > 0)
                            c.clipRect(itemView.left, itemView.top, dX.toInt(), itemView.bottom)
                        else
                            c.clipRect(
                                itemView.right + dX.toInt(),
                                itemView.top,
                                itemView.right,
                                itemView.bottom
                            )

                        deleteIcon.draw(c)

                        c.restore()

                        super.onChildDraw(
                            c,
                            recyclerView,
                            viewHolder,
                            dX,
                            dY,
                            actionState,
                            isCurrentlyActive
                        )
                    }
                }

            val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
            itemTouchHelper.attachToRecyclerView(roomsRecycler)

            swipeContainer.setOnRefreshListener {
                roomsViewModel.getRoomList()
                swipeContainer.isRefreshing = false
            }
        }

    }

    override fun onDestroyView() {
        binding.roomsRecycler.adapter = null
        super.onDestroyView()
    }
}
