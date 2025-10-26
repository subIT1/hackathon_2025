package com.example.hackathon.ble.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

object LocationProvider {
    private fun hasLocationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 23) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else true

    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return null
            val gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            when {
                gps != null && net != null -> if (gps.time >= net.time) gps else net
                gps != null -> gps
                else -> net
            }
        } catch (_: Exception) {
            null
        }
    }
}