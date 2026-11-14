package com.example.getyourmoneyupnotyourfunnyup.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class ConnectionState { Unknown, Online, Offline }

class ConnectivityObserver(context: Context) {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    fun observe() = callbackFlow {
        if (manager == null) {
            trySend(ConnectionState.Unknown)
            awaitClose { }
            return@callbackFlow
        }
        fun publish() {
            val capabilities = try {
                manager.getNetworkCapabilities(manager.activeNetwork)
            } catch (error: SecurityException) {
                trySend(ConnectionState.Unknown)
                return
            }
            trySend(if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                ConnectionState.Online
            } else ConnectionState.Offline)
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publish()
            override fun onLost(network: Network) = publish()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = publish()
        }
        try {
            manager.registerDefaultNetworkCallback(callback)
            publish()
        } catch (error: SecurityException) {
            trySend(ConnectionState.Unknown)
            close()
            return@callbackFlow
        }
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
