package app.quarantine.android.blocking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RelockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != QuarantineService.ACTION_RELOCK) return
        val target = intent.getStringExtra(QuarantineService.EXTRA_PACKAGE) ?: return
        val token = intent.getStringExtra(QuarantineService.EXTRA_TOKEN) ?: return
        QuarantineService.instance?.relock(target, token)
    }
}
