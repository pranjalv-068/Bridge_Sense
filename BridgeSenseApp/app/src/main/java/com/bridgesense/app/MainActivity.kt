package com.bridgesense.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.bridgesense.app.databinding.ActivityMainBinding
import com.bridgesense.app.network.ConnectionState
import com.bridgesense.app.ui.BridgeViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * MainActivity
 *
 * Single-Activity architecture with Navigation Component.
 * Hosts 4 fragments via BottomNavigationView:
 *   1. Dashboard   — Node grid + health score
 *   2. Camera      — YOLOX photo capture
 *   3. Alerts      — Real-time alert feed
 *   4. Settings    — Connection + mode config
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    lateinit var viewModel: BridgeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[BridgeViewModel::class.java]

        setupNavigation()
        observeState()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNav.setupWithNavController(navController)

        // Show/hide alert badge on the Alerts nav item
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val showNav = destination.id in listOf(
                R.id.dashboardFragment,
                R.id.cameraFragment,
                R.id.alertsFragment,
                R.id.settingsFragment
            )
            binding.bottomNav.visibility = if (showNav) View.VISIBLE else View.GONE
        }
    }

    private fun observeState() {
        // Show snackbar for UI messages (upload success/fail, connection results)
        lifecycleScope.launch {
            viewModel.uiMessage.collectLatest { msg ->
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).apply {
                    setBackgroundTint(getColor(R.color.bg_card_elevated))
                    setTextColor(getColor(R.color.text_primary))
                    show()
                }
            }
        }

        // Update connection dot in bottom nav / header
        lifecycleScope.launch {
            viewModel.connectionState.collectLatest { state ->
                updateConnectionIndicator(state)
            }
        }

        // Update alert badge count
        lifecycleScope.launch {
            viewModel.alerts.collectLatest { alerts ->
                val unread = alerts.count { !it.isRead }
                val badge = binding.bottomNav.getOrCreateBadge(R.id.alertsFragment)
                if (unread > 0) {
                    badge.isVisible = true
                    badge.number = unread
                    badge.backgroundColor = getColor(R.color.status_critical)
                } else {
                    badge.isVisible = false
                }
            }
        }
    }

    private fun updateConnectionIndicator(state: ConnectionState) {
        val color = when (state) {
            ConnectionState.CONNECTED    -> getColor(R.color.status_normal)
            ConnectionState.CONNECTING   -> getColor(R.color.status_warning)
            ConnectionState.DISCONNECTED -> getColor(R.color.status_offline)
        }
        binding.connectionDot.setColorFilter(color)

        val label = when (state) {
            ConnectionState.CONNECTED    -> if (viewModel.isMockMode) "MOCK" else "LIVE"
            ConnectionState.CONNECTING   -> "CONNECTING"
            ConnectionState.DISCONNECTED -> if (viewModel.isMockMode) "MOCK" else "OFFLINE"
        }
        binding.tvConnectionLabel.text = label

        val labelColor = when (state) {
            ConnectionState.CONNECTED    -> getColor(R.color.status_normal)
            ConnectionState.CONNECTING   -> getColor(R.color.status_warning)
            ConnectionState.DISCONNECTED -> if (viewModel.isMockMode) getColor(R.color.accent_cyan)
                                           else getColor(R.color.status_offline)
        }
        binding.tvConnectionLabel.setTextColor(labelColor)
    }
}
