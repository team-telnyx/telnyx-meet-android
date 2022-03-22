package com.telnyx.meet.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bugsnag.android.Bugsnag
import com.telnyx.meet.BuildConfig
import com.telnyx.meet.R
import com.telnyx.meet.navigator.Navigator
import com.telnyx.meet.ui.factory.DefaultFragmentFactoryEntryPoint
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import javax.inject.Inject

@AndroidEntryPoint
class ManageRoomActivity : AppCompatActivity() {

    @Inject
    lateinit var navigator: Navigator

    private lateinit var navHostFragment: Fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        val entryPoint =
            EntryPointAccessors.fromActivity(
                this,
                DefaultFragmentFactoryEntryPoint::class.java
            )

        supportFragmentManager.fragmentFactory = entryPoint.getFragmentFactory()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!BuildConfig.IS_APP_TESTING.get()) {
            Bugsnag.start(applicationContext)
        }

        navHostFragment =
            requireNotNull(supportFragmentManager.findFragmentById(R.id.fragmentContainer))

        val navController = navHostFragment.findNavController()

        navigator.navController = navController
    }

    fun setActionBarTitle(title: String?) {
        supportActionBar?.title = title
    }
}
