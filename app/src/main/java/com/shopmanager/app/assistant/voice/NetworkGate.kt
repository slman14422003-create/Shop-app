package com.shopmanager.app.assistant.voice

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class NetworkGate(context: Context) {
    private val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    fun isAllowed(allowMobile: Boolean): Boolean {
        val manager = cm ?: return true
        return try {
            val n = manager.activeNetwork ?: return false
            val caps = manager.getNetworkCapabilities(n) ?: return false
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
            if (allowMobile) return true
            val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            val wifiOrEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            wifiOrEthernet && unmetered
        } catch (t: Throwable) {
            true
        }
    }

    fun isOnWifi(): Boolean = isAllowed(false)

    fun awaitAllowed(allowMobile: Boolean, cancel: AtomicBoolean): Boolean {
        if (isAllowed(allowMobile)) return true
        val manager = cm
        val signal = Semaphore(0)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                signal.release()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                signal.release()
            }
        }
        var registered = false
        try {
            if (manager != null) {
                manager.registerNetworkCallback(
                    NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                    callback
                )
                registered = true
            }
        } catch (t: Throwable) {
            registered = false
        }
        try {
            while (!cancel.get()) {
                if (isAllowed(allowMobile)) return true
                signal.tryAcquire(2, TimeUnit.SECONDS)
            }
            return false
        } finally {
            if (registered) {
                try {
                    manager?.unregisterNetworkCallback(callback)
                } catch (t: Throwable) {
                }
            }
        }
    }
}
