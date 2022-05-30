package com.telnyx.meet.ui

import android.content.Context
import android.provider.Settings.Global.*
import android.view.View
import android.widget.TextView
import androidx.test.espresso.*
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.*
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.rule.ActivityTestRule
import com.telnyx.meet.R
import com.telnyx.meet.testhelpers.BaseUITest
import com.telnyx.meet.ui.adapters.ParticipantTileAdapter
import com.telnyx.meet.ui.adapters.RoomAdapter
import org.hamcrest.Matchers.allOf
import org.junit.*
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.concurrent.TimeoutException

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ManageRoomDetailsActivityTest : BaseUITest() {

    @get:Rule
    val activityRule = ActivityTestRule(ManageRoomActivity::class.java, false, false)

    private lateinit var context: Context
    private lateinit var roomName: String

    @Before
    fun setUp() {
        context = getInstrumentation().targetContext.applicationContext
        roomName = "000 Test Room"
        Intents.init()
        setAnimations(false)
    }

    @After
    fun tearDown() {
        Intents.release()
        setAnimations(true)
    }

    private fun setAnimations(enabled: Boolean) {
        val value = if (enabled) "1.0" else "0.0"
        getInstrumentation().uiAutomation.run {
            this.executeShellCommand("settings put global $WINDOW_ANIMATION_SCALE $value")
            this.executeShellCommand("settings put global $TRANSITION_ANIMATION_SCALE $value")
            this.executeShellCommand("settings put global $ANIMATOR_DURATION_SCALE $value")
        }
    }

    @Test
    fun on_A_LaunchWithoutExceptionThrown() {
        assertDoesNotThrow {
            activityRule.launchActivity(null)
            onView(withId(R.id.join_explainer_text)).check(matches(isDisplayed()))
            onView(withId(R.id.buttonJoinRoom)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun on_B_CreateARoomShowsEmptyNamesScreen() {
        assertDoesNotThrow {
            activityRule.launchActivity(null)
            onView(withId(R.id.see_available_rooms_text)).perform(click())
            onView(withId(R.id.fab)).perform(click())
            onView(withId(R.id.label_room_name)).check(matches(isDisplayed()))
            onView(allOf(withId(R.id.tv_room_name), withText("..."))).check(matches(isDisplayed()))
            onView(withId(R.id.label_room_id)).check(matches(isDisplayed()))
            onView(withId(R.id.label_token_id)).check(matches(isDisplayed()))
            onView(withHint("Enter a Room name")).check(matches(isDisplayed()))
            onView(withId(R.id.buttonCreateRoom)).check(matches(isEnabled()))
        }
    }

    @Test
    fun on_C_testEmptyRoomNameCausesError() {
        assertDoesNotThrow {
            activityRule.launchActivity(null)
            onView(withId(R.id.see_available_rooms_text)).perform(click())
            onView(withId(R.id.fab)).perform(click())
            onView(withId(R.id.buttonCreateRoom)).check(matches(isEnabled()))
            onView(withId(R.id.buttonCreateRoom)).perform(click())
            onView(withText("You should provide a unique name for your room")).check(
                matches(
                    isDisplayed()
                )
            )
        }
    }

    @Test
    fun on_D_testAccessToGivenRoomToggleVideoThenAudioCheckStats() {
        assertDoesNotThrow {
            activityRule.launchActivity(null)
            onView(withId(R.id.see_available_rooms_text)).perform(click())
            onView(withId(R.id.participant_name_et)).waitUntilVisible(25000).perform(
                setTextInTextView("Test Participant"),
                closeSoftKeyboard()
            )
            Thread.sleep(2000)
            onView(withId(R.id.roomsRecycler)).perform(
                scrollTo<RoomAdapter.RoomHolder>(
                    hasDescendant(
                        withText(roomName)
                    )
                )
            )
            onView(withId(R.id.roomsRecycler)).perform(
                actionOnItem<RoomAdapter.RoomHolder>(
                    hasDescendant(
                        withText(
                            roomName
                        )
                    ),
                    click()
                )
            )
            onView(withId(R.id.action_camera)).waitUntilVisible(15000).perform(click())
            Thread.sleep(2000)
            onView(withId(R.id.action_mic)).perform(click())
            Thread.sleep(2000)
            onView(withId(R.id.participantTileRecycler)).waitUntilVisible(15000).perform(
                actionOnItem<ParticipantTileAdapter.ParticipantTileHolder>(
                    hasDescendant(
                        withText(
                            "Test Participant"
                        )
                    ),
                    click()
                )
            )
            Thread.sleep(2000)
            onView(withId(R.id.stats_dialog_title)).waitUntilVisible(15000)
                .check(matches(isDisplayed()))
            Thread.sleep(2000)
            onView(withId(R.id.stats_dialog_id)).waitUntilVisible(15000).perform(customSwipeDown())
            Thread.sleep(2000)
            onView(withId(R.id.action_camera)).perform(click())
            Thread.sleep(2000)
            onView(withId(R.id.action_mic)).perform(click())
            Thread.sleep(2000)
        }
    }

    @Test
    fun on_E_testAccessToGivenRoomToggleAudioThenVideo() {
        assertDoesNotThrow {
            activityRule.launchActivity(null)
            onView(withId(R.id.see_available_rooms_text)).perform(click())
            Thread.sleep(2000)
            onView(withId(R.id.roomsRecycler)).waitUntilVisible(15000).perform(
                scrollTo<RoomAdapter.RoomHolder>(
                    hasDescendant(
                        withText(roomName)
                    )
                )
            )
            onView(withId(R.id.roomsRecycler)).perform(
                actionOnItem<RoomAdapter.RoomHolder>(
                    hasDescendant(
                        withText(
                            roomName
                        )
                    ),
                    click()
                )
            )
            onView(withId(R.id.action_mic)).waitUntilVisible(25000).perform(click())
            Thread.sleep(2000)
            onView(withId(R.id.action_camera)).perform(click())
            Thread.sleep(2000)
            onView(withId(R.id.action_mic)).perform(click())
            Thread.sleep(2000)
            onView(withId(R.id.action_camera)).perform(click())
            Thread.sleep(2000)
        }
    }

    @Test
    fun on_F_testAccessToGivenRoomNavToChatSendMessage() {
        assertDoesNotThrow {
            activityRule.launchActivity(null)
            onView(withId(R.id.see_available_rooms_text)).perform(click())
            Thread.sleep(2000)
            onView(withId(R.id.roomsRecycler)).waitUntilVisible(15000).perform(
                scrollTo<RoomAdapter.RoomHolder>(
                    hasDescendant(
                        withText(roomName)
                    )
                )
            )
            onView(withId(R.id.roomsRecycler)).perform(
                actionOnItem<RoomAdapter.RoomHolder>(
                    hasDescendant(
                        withText(
                            roomName
                        )
                    ),
                    click()
                )
            )
            Thread.sleep(15000)
            openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
            onView(withText("Open Chat")).waitUntilVisible(15000).perform(click())
            onView(withId(R.id.chatRecyclerLayout)).waitUntilVisible(15000)
                .check(matches(isDisplayed()))
            onView(withId(R.id.chat_edit_text)).waitUntilVisible(15000).perform(
                setTextInTextView("Test Message to send"),
                closeSoftKeyboard()
            )
            Thread.sleep(2000)
            onView(withId(R.id.chat_send_btn)).perform(click())
            Thread.sleep(1000)
        }
    }

    @Test
    fun on_G_testAccessToGivenRoomNavToParticipantsList() {
        assertDoesNotThrow {
            activityRule.launchActivity(null)
            onView(withId(R.id.see_available_rooms_text)).perform(click())
            Thread.sleep(2000)
            onView(withId(R.id.roomsRecycler)).waitUntilVisible(25000).perform(
                scrollTo<RoomAdapter.RoomHolder>(
                    hasDescendant(
                        withText(roomName)
                    )
                )
            )
            onView(withId(R.id.roomsRecycler)).perform(
                actionOnItem<RoomAdapter.RoomHolder>(
                    hasDescendant(
                        withText(
                            roomName
                        )
                    ),
                    click()
                )
            )
            Thread.sleep(15000)
            openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
            onView(withText("View participants")).waitUntilVisible(15000).perform(click())
            Thread.sleep(2000)
            onView(withId(R.id.participantRecycler)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun on_H_testParticipantNameNotEmpty() {
        activityRule.launchActivity(null)
        onView(withId(R.id.see_available_rooms_text)).perform(click())
        onView(withId(R.id.participant_name_et)).waitUntilVisible(25000).perform(clearText())
        onView(withId(R.id.fab)).perform(click())
        Thread.sleep(1000)
        onView(withText(R.string.provide_participant_name_error)).check(matches(isDisplayed()))
        onView(withId(R.id.participant_name_et)).perform(
            setTextInTextView("SomeName"),
            closeSoftKeyboard()
        )
        Thread.sleep(1000)
        onView(withText(R.string.provide_participant_name_error)).check(doesNotExist())
    }

    private fun setTextInTextView(value: String?): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): org.hamcrest.Matcher<View> {
                return allOf(isDisplayed(), isAssignableFrom(TextView::class.java))
            }

            override fun perform(uiController: UiController, view: View) {
                (view as TextView).text = value
            }

            override fun getDescription(): String {
                return "replace text"
            }
        }
    }

    private fun customSwipeDown(): ViewAction {
        return GeneralSwipeAction(
            Swipe.FAST,
            GeneralLocation.TOP_CENTER,
            GeneralLocation.BOTTOM_CENTER,
            Press.FINGER
        )
    }
}

/**
 * Wait for view to be visible
 */
fun ViewInteraction.waitUntilVisible(timeout: Long): ViewInteraction {
    val startTime = System.currentTimeMillis()
    val endTime = startTime + timeout

    do {
        try {
            check(matches(isDisplayed()))
            return this
        } catch (e: NoMatchingViewException) {
            Thread.sleep(50)
        }
    } while (System.currentTimeMillis() < endTime)

    throw TimeoutException()
}