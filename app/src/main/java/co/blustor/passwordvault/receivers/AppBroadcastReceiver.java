package co.blustor.passwordvault.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import co.blustor.passwordvault.activities.UnlockActivity;
import co.blustor.passwordvault.constants.Intents;
import co.blustor.passwordvault.database.Vault;
import co.blustor.passwordvault.services.NotificationService;

public class AppBroadcastReceiver extends BroadcastReceiver {
    protected static final String TAG = "AppBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null) {
            if (action.equals(Intents.LOCK_DATABASE)) {
                Log.d(TAG, "Lock database");
                Vault vault = Vault.getInstance(context);
                vault.lock();

                Intent unlockActivity = new Intent(context, UnlockActivity.class);
                unlockActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); // Removes other Activities from stack
                context.startActivity(unlockActivity);

                // TODO: The above opens the app.  This is not ideal.

                Intent ongoingNotificationService = new Intent(context, NotificationService.class);
                context.startService(ongoingNotificationService);
            }
        }
    }
}
