package com.santhosh.easynavi

import android.location.Location

/**
 * Created by aus8kor on 3/8/2018.
 */
class RouteStepInfo {
    var startLocation: Location? = null
    var endLocation: Location? = null
    var direction: String? = ""
    var distance: Int? = 0


    override fun toString(): String {
        return "startLocation: $startLocation, endLocation: $endLocation, direction: $direction, distance: $distance"
    }
}

