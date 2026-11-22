package com.gathin.tvhome;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/** Network state available on API 19; newer callback APIs are deliberately avoided here. */
final class NetworkCompat {
    static final class State {
        boolean online;
        boolean validated;
        boolean vpn;
        boolean wifi;
        boolean ethernet;
    }

    private NetworkCompat() {
    }

    static State read(Context context) {
        State state = new State();
        ConnectivityManager cm = (ConnectivityManager) ApiCompat.service(
                context.getApplicationContext(), Context.CONNECTIVITY_SERVICE);
        if (cm == null) return state;
        try {
            NetworkInfo[] all = cm.getAllNetworkInfo();
            if (all != null) {
                for (NetworkInfo info : all) {
                    if (info == null || !info.isConnected()) continue;
                    state.online = true;
                    state.validated = true;
                    int type = info.getType();
                    if (type == ConnectivityManager.TYPE_VPN) state.vpn = true;
                    if (type == ConnectivityManager.TYPE_WIFI) state.wifi = true;
                    if (type == ConnectivityManager.TYPE_ETHERNET) state.ethernet = true;
                }
            }
            NetworkInfo active = cm.getActiveNetworkInfo();
            if (active != null && active.isConnected()) {
                state.online = true;
                state.validated = true;
            }
        } catch (RuntimeException ignored) {
        }
        return state;
    }
}
