package com.santhosh.gonavi.adapter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.support.v4.app.ActivityCompat
import android.widget.Toast
import com.google.android.gms.location.*
import com.santhosh.gonavi.inf.OnLocationChangedListener

/**
 * This shows how to create a simple activity with a map and a marker on the map.
 */
public class MyCurrentLocation(private val mContext: Context, private val mListener: OnLocationChangedListener) {
    private var mLocationRequest: LocationRequest? = null
    private var mFusedLocationClient: FusedLocationProviderClient? = null

    //Constants
    private val LOCATION_FAST_INTERVAL: Long? = 2000;
    private val LOCATION_INTERVAL: Long? = 10000;

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                // Update UI with location data
                // ...
                mListener.onLocationChanged(location);
            }
        }
    }

    @Synchronized
    fun buildFusedLocationClient(context: Context) {

        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setInterval(LOCATION_INTERVAL!!)        // 10 seconds, in milliseconds
                .setFastestInterval(LOCATION_FAST_INTERVAL!!) // 5 seconds, in milliseconds
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(mContext)

    }

    fun start() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(mContext, "permission ACCESS_FINE_LOCATION not granted", Toast.LENGTH_SHORT).show()
        }
        mFusedLocationClient!!.requestLocationUpdates(mLocationRequest, mLocationCallback, null)
    }

    fun stop() {
        mFusedLocationClient!!.removeLocationUpdates(mLocationCallback)
    }

}