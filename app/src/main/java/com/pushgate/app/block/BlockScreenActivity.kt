package com.pushgate.app.block

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.pushgate.app.data.db.EventLog
import com.pushgate.app.data.repo.BlockDecision
import com.pushgate.app.data.repo.BlockRepository
import com.pushgate.app.ui.challenge.ChallengeActivity
import com.pushgate.app.ui.theme.PushGateTheme
import kotlinx.coroutines.launch

/**
 * The wall.
 *
 * Two faces: the ordinary "you are out of budget" interception, and the Strict Mode notice shown
 * when someone tries to walk into Settings and switch protection off.
 *
 * Back is deliberately inert — it drops the user to the home screen rather than returning to the
 * app that was just blocked.
 */
class BlockScreenActivity : ComponentActivity() {

    private lateinit var repo: BlockRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        repo = BlockRepository.get(this)

        setContent {
            PushGateTheme(forceDark = true) {
                val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_BLOCK

                BackHandler(enabled = true) { goHome() }

                if (mode == MODE_TAMPER) {
                    TamperHost(
                        cooldownMinutes = intent.getIntExtra(EXTRA_COOLDOWN_MINUTES, 30),
                        onStartCooldown = { startCooldown(it) },
                        onDismiss = { goHome() }
                    )
                } else {
                    BlockHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    @androidx.compose.runtime.Composable
    private fun BlockHost() {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        var reps by remember { mutableStateOf(intent.getIntExtra(EXTRA_REPS, 5)) }
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 2)

        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                openTargetApp(pkg)
            }
        }

        BlockScreen(
            packageName = pkg,
            label = label,
            budgetMs = intent.getLongExtra(EXTRA_BUDGET_MS, 0L),
            usedMs = intent.getLongExtra(EXTRA_USED_MS, 0L),
            earnedUnlocksToday = intent.getIntExtra(EXTRA_EARNED_TODAY, 0),
            canEarn = intent.getBooleanExtra(EXTRA_CAN_EARN, true),
            repsRequired = reps,
            minutesOffered = minutes,
            onStartChallenge = {
                launcher.launch(ChallengeActivity.intent(this, pkg, label, reps, minutes))
            },
            onDismiss = { goHome() }
        )
    }

    private fun openTargetApp(pkg: String) {
        val launch = AppLabels.launchIntentFor(this, pkg)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(launch) }
        }
        finish()
    }

    private fun startCooldown(minutes: Int) {
        lifecycleScope.launch {
            val endsAt = System.currentTimeMillis() + minutes * 60_000L
            repo.settingsStore.setCooldownEndsAt(endsAt)
            repo.logEvent(EventLog.COOLDOWN_STARTED, detail = "$minutes min")
            goHome()
        }
    }

    private fun goHome() {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
    }

    companion object {
        const val MODE_BLOCK = "block"
        const val MODE_TAMPER = "tamper"

        private const val EXTRA_MODE = "mode"
        private const val EXTRA_PACKAGE = "pkg"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_BUDGET_MS = "budget"
        private const val EXTRA_USED_MS = "used"
        private const val EXTRA_EARNED_TODAY = "earned"
        private const val EXTRA_CAN_EARN = "can_earn"
        private const val EXTRA_REPS = "reps"
        private const val EXTRA_MINUTES = "minutes"
        private const val EXTRA_COOLDOWN_MINUTES = "cooldown"

        private const val FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TASK or
            Intent.FLAG_ACTIVITY_NO_ANIMATION or
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS

        fun blockIntent(context: Context, d: BlockDecision.Blocked): Intent =
            Intent(context, BlockScreenActivity::class.java)
                .addFlags(FLAGS)
                .putExtra(EXTRA_MODE, MODE_BLOCK)
                .putExtra(EXTRA_PACKAGE, d.packageName)
                .putExtra(EXTRA_LABEL, d.label)
                .putExtra(EXTRA_BUDGET_MS, d.budgetMs)
                .putExtra(EXTRA_USED_MS, d.usedMs)
                .putExtra(EXTRA_EARNED_TODAY, d.earnedUnlocksToday)
                .putExtra(EXTRA_CAN_EARN, d.canEarn)
                .putExtra(EXTRA_REPS, d.repsRequired)
                .putExtra(EXTRA_MINUTES, d.minutesOffered)

        fun tamperIntent(context: Context, cooldownMinutes: Int): Intent =
            Intent(context, BlockScreenActivity::class.java)
                .addFlags(FLAGS)
                .putExtra(EXTRA_MODE, MODE_TAMPER)
                .putExtra(EXTRA_COOLDOWN_MINUTES, cooldownMinutes)
    }
}
