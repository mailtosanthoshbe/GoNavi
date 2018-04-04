package com.santhosh.gonavi.activity

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.Camera
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.tts.TextToSpeech
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.santhosh.easynavi.MapUtil
import com.santhosh.easynavi.RouteStepInfo
import com.santhosh.gonavi.R
import com.santhosh.gonavi.adapter.MyCurrentLocation
import com.santhosh.gonavi.adapter.MySpeechRecognitionManager
import com.santhosh.gonavi.inf.MySpeechRecognitionCallback
import com.santhosh.gonavi.inf.OnLocationChangedListener
import java.util.*

class MainActivity : AppCompatActivity(),
        OnMapReadyCallback, SurfaceHolder.Callback, OnLocationChangedListener {

    private var tts: TextToSpeech? = null
    private var mCameraSurfaceView: SurfaceView? = null
    private var mPathSurfaceView: SurfaceView? = null
    private var mCamera: Camera? = null
    private var isCameraviewOn = false
    private var mPath: Path? = null
    private var mFusedLocationProviderClient: FusedLocationProviderClient? = null
    private var mMap: GoogleMap? = null
    private var myCurrentLocation: MyCurrentLocation? = null
    private var mRouteList: Deque<RouteStepInfo>? = null
    private var nextStepInfo: RouteStepInfo? = null
    private var mPolylineList: MutableList<LatLng>? = null
    private var mapUtil: MapUtil? = null

    //Put any keyword that will trigger the speech recognition
    companion object {
        private const val TAG = "MainActivity"
        private const val ACTIVATION_KEYWORD = "Santa"
        private const val REQUEST_ID_MULTIPLE_PERMISSIONS = 1
        private const val REQUEST_SPEECH_NORMAL = 100
        private const val REQUEST_SPEECH_CALL = 101
        private const val ZOOM_LEVEL = 13f

    }

    lateinit var recognitionManager: MySpeechRecognitionManager

    /**
     *
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Check and Enable permission(>Android 6.0)
        checkAndRequestPermissions()

        //inflate map view
        val mapFragment: SupportMapFragment? =
                supportFragmentManager.findFragmentById(R.id.map_fragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(applicationContext)

        //Rear Camera init
        mCameraSurfaceView = findViewById(R.id.camera_view)
        mCameraSurfaceView!!.holder.addCallback(this)
        mCameraSurfaceView!!.setSecure(true)

        //Path Render init
        mPathSurfaceView = findViewById(R.id.path_view)
        mPathSurfaceView!!.holder.addCallback(this)
        mPathSurfaceView!!.setZOrderOnTop(true)
        mPathSurfaceView!!.holder.setFormat(PixelFormat.TRANSLUCENT)

        //Speech Recognition
        recognitionManager = MySpeechRecognitionManager(this, activationKeyword = ACTIVATION_KEYWORD, callback = object : MySpeechRecognitionCallback {
            override fun onKeywordDetected() {
                Log.d(TAG, "onKeywordDetected")
            }

            override fun onResults(results: List<String>, scores: FloatArray?, mode: Int) {
                Log.d(TAG, "onResults: $results, $scores, $mode")
                when (mode) {
                    REQUEST_SPEECH_CALL -> {
                        //CALL Mode
                        if (results[0].equals("yes", ignoreCase = true)) {
                            //stop recognition until call ends
                            recognitionManager.stopRecognition()
                        }
                    }
                    else -> {
                        Log.d(TAG, "onResults: None")
                    }
                }
                recognitionManager.muteRecognition(false);
            }

            override fun onError(errorMsg: String) {
                Log.d(TAG, "onError: $errorMsg")
            }

            override fun onEndOfSpeech() {
                Log.d(TAG, "onEndOfSpeech")
            }
        })

        //TTS Support
        tts = TextToSpeech(this, TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts!!.setLanguage(Locale.UK)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TextToSpeech: TTS - Language is not supported")
                } else {
                    //speakText("Welcome to My App!");
                    Log.d(TAG, "TextToSpeech: TTS - Init Success")
                }
            } else {
                Log.e(TAG, "TextToSpeech: TTS - Initilization Failed")
            }
        })

        //init location listener
        myCurrentLocation = MyCurrentLocation(applicationContext, this)
        myCurrentLocation!!.buildFusedLocationClient(this)
        myCurrentLocation!!.start()

        //init utils
        mPath = Path()
        nextStepInfo = RouteStepInfo()
        mRouteList = ArrayDeque()
        mPolylineList = ArrayList()
        mapUtil = MapUtil()
    }

    /**
     *
     */
    private var mCallReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent!!.getStringExtra(TelephonyManager.EXTRA_STATE)
            when (state) {
                null -> {
                    //Outgoing call
                    val number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                    Log.d(TAG, "onReceive: Outgoing number : $number")
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    Log.d(TAG, "onReceive: EXTRA_STATE_OFFHOOK")
                    Toast.makeText(context, "onReceive: EXTRA_STATE_OFFHOOK", Toast.LENGTH_SHORT).show()
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    Log.d(TAG, "onReceive: EXTRA_STATE_IDLE")
                    //start recognition, if stopped
                    recognitionManager.muteRecognition(false)
                    recognitionManager.startRecognition(REQUEST_SPEECH_NORMAL)
                    Toast.makeText(context, "onReceive: EXTRA_STATE_IDLE", Toast.LENGTH_SHORT).show()
                }
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    //Incoming call
                    val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    Log.d(TAG, "onReceive: Incoming number : $number")
                    //Toast.makeText(context, "onReceive: Incoming number: $number", Toast.LENGTH_SHORT).show()
                    //Alert user about incoming call and wait for user action
                    recognitionManager.stopRecognition()
                    recognitionManager.muteRecognition(true)
                    var name = getContactName(number, applicationContext)
                    if (name.isEmpty()) {
                        name = number
                    }
                    speakText("Incoming call from $name. Say yes to accept or no to deny.")
                    recognitionManager.startRecognition(REQUEST_SPEECH_CALL)
                    /*val i = Intent()
                        i.setClass(context, CallActivity::class.java)
                        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        i.putExtra("INCOMING_NUMBER", number)
                        i.action = Intent.ACTION_MAIN
                        i.addCategory(Intent.CATEGORY_LAUNCHER)
                        context!!.startActivity(i)*/
                }
                else -> {
                    Log.d(TAG, "onReceive: none")
                    Toast.makeText(context, "onReceive: none", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     *
     */
    private fun speakText(message: String?) {
        if (message == null) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.speak(message, TextToSpeech.QUEUE_ADD, null, null)
        } else {
            tts?.speak(message, TextToSpeech.QUEUE_ADD, null)
        }
    }

    /**
     *
     */
    override fun onStart() {
        super.onStart()
        //Register for obtaining user's activity recognition
        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        filter.priority = 999
        registerReceiver(mCallReceiver, filter)
    }

    /**
     *
     */
    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            recognitionManager.startRecognition(REQUEST_SPEECH_NORMAL)
        }
    }

    /**
     *
     */
    override fun onPause() {
        Log.d(TAG, "onPause!!")
        recognitionManager.cancelRecognition()
        super.onPause()
    }

    /**
     *
     */
    override fun onStop() {
        Log.d(TAG, "onStop!!")
        unregisterReceiver(mCallReceiver)
        super.onStop()
    }

    /**
     *
     */
    override fun onDestroy() {
        Log.d(TAG, "onDestroy!!")
        recognitionManager.destroyRecognizer()
        tts?.stop()
        super.onDestroy()
    }

    /**
     * checkPermission
     */
    private fun checkPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     *
     */
    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)

        val listPermissionsNeeded = mutableListOf<String>()
        for (permission in permissions) {
            if (!checkPermission(this, permission)) {
                listPermissionsNeeded.add(permission)
            }
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    listPermissionsNeeded.toTypedArray(),
                    REQUEST_ID_MULTIPLE_PERMISSIONS)
        } else {
            Log.d(TAG, "All Permissions granted!")
        }
    }

    /**
     *
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_ID_MULTIPLE_PERMISSIONS -> {
                if (grantResults.isNotEmpty()) {
                    for (i in grantResults.indices) {
                        when (grantResults[i]) {
                            PackageManager.PERMISSION_GRANTED -> Log.d(TAG, permissions[i] + ": PERMISSION GRANTED")
                            else -> Log.d(TAG, permissions[i] + ": PERMISSION DENIED")
                        }
                    }
                } else {
                    Log.d(TAG, "PERMISSION_DENIED: Go to settings and enable permissions!")
                }
            }
        }
    }

    /**
     *
     */
    private fun getContactName(phoneNumber: String, context: Context): String {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        var contactName = ""
        val cursor = context.contentResolver.query(uri, projection, null,
                null, null)
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                contactName = cursor.getString(0)
            }
            cursor.close()
        }
        return contactName
    }

    /**
     *
     */
    override fun onLocationChanged(currentLocation: Location?) {
        Log.d(TAG, "onLocationChanged: ${currentLocation?.latitude}, ${currentLocation?.longitude}")
        /*val distance = currentLocation!!.distanceTo(nextStepInfo?.startLocation!!)
        Log.d(TAG, "onLocationChanged:: distance: $distance")
        if (distance < 50) {
            speakText(nextStepInfo!!.direction)

            //pop the next iteration
            nextStepInfo = mRouteList?.remove()
        }*/

    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just move the camera to Sydney and add a marker in Sydney.
     */
    override fun onMapReady(googleMap: GoogleMap?) {
        googleMap ?: return
        with(googleMap) {
            Log.d(TAG, "onMapReady::")
            //addMarker(MarkerOptions().position(SYDNEY))
            mMap = googleMap
            val permission = ContextCompat.checkSelfPermission(applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION)
            if (permission == PackageManager.PERMISSION_GRANTED) {
                googleMap.isMyLocationEnabled = true

                val locationResult = mFusedLocationProviderClient?.lastLocation
                locationResult?.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Set the map's camera position to the current location of the device.
                       /* val location = task.result
                        val currentLatLng = LatLng(location.latitude, location.longitude)
                        val update = CameraUpdateFactory.newLatLngZoom(currentLatLng, ZOOM_LEVEL)
                        googleMap.moveCamera(update)*/
                        googleMap.uiSettings.isMyLocationButtonEnabled = true

                       /* val options = PolylineOptions()
                        options.color(Color.RED)
                        options.addAll(mPolylineList)
                        //options.width(5f)
                        val src = mPolylineList?.get(0)
                        val dest = mPolylineList?.get(mPolylineList?.size?.minus(1)!!)
                        val LatLongB = LatLngBounds.Builder()
                        LatLongB.include(src) //Source Location
                        LatLongB.include(dest) //Dest location
                        googleMap.addPolyline(options)
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLongB.build(), 100))

                        //add markers
                        addMarker(MarkerOptions().position(src!!).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))
                        addMarker(MarkerOptions().position(dest!!))*/
                        //update view
                        //onLocationChanged(location)
                    }
                }
            } else {
                /*requestPermission(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        LOCATION_REQUEST_CODE)*/
            }
            //googleMap?.mapType = GoogleMap.MAP_TYPE_SATELLITE;
            googleMap.animateCamera(CameraUpdateFactory.zoomIn())

        }
    }

    /**
     * Returns an ordinal value for the SurfaceHolder, or -1 for an invalid surface.
     */
    private fun getSurfaceId(holder: SurfaceHolder): Int {
        return if (holder == mCameraSurfaceView!!.holder) {
            1
        } else if (holder == mPathSurfaceView!!.holder) {
            2
        } else {
            -1
        }
    }

    /**
     * surfaceDestroyed
     */
    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        Log.d(TAG, "Surface destroyed holder=" + holder)
        mCamera?.stopPreview()
        mCamera?.release()
        mCamera = null
        isCameraviewOn = false
    }

    /**
     * surfaceCreated
     */
    override fun surfaceCreated(holder: SurfaceHolder?) {
        /*if (getSurfaceId(holder!!) < 0) {

        } else {
            Log.d(TAG, "surfaceCreated holder=" + holder);
        }*/

        when (getSurfaceId(holder!!)) {
            1 -> {
                //init camera
                mCamera = Camera.open()
                //mCamera!!.setDisplayOrientation(90)
                if (isCameraviewOn) {
                    mCamera!!.stopPreview()
                    isCameraviewOn = false
                }
            }
            2 -> {
                //path overlay
                mPath?.reset()
            }
            else -> {
                Log.w(TAG, "surfaceCreated UNKNOWN holder=" + holder)
            }
        }
    }

    /**
     * surfaceChanged
     */
    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        val id = getSurfaceId(holder!!)
        when (id) {
            1 -> {
                //Camera Surface
                mCamera!!.setPreviewDisplay(holder)
                mCamera!!.startPreview()
                isCameraviewOn = true
            }
            2 -> {
                //Overlay path render surface
                drawPath(holder.surface, width, height)
            }
            else -> {
                Log.d(TAG, "surfaceChanged nothing");
            }
        }
    }

    /**
     * Returns an ordinal value for the SurfaceHolder, or -1 for an invalid surface.
     */
    private fun drawPath(surface: Surface, width: Int, height: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.CYAN                    // set the color
        paint.strokeWidth = 22f               // set the size
        paint.isDither = true                    // set the dither to true
        paint.style = Paint.Style.STROKE       // set to STOKE
        //paint.setStrokeJoin(Paint.Join.ROUND);    // set the join to round you want
        paint.strokeCap = Paint.Cap.ROUND      // set the paint cap to round too
        paint.pathEffect = CornerPathEffect(10f)   // set the path effect when they join.
        paint.isAntiAlias = true
        paint.alpha = 100

        //from middle bottom to center
        /*mPath?.moveTo(width/2.toFloat(), height-100.toFloat())
        for (i in height-100 downTo 100) {
            //mPath.moveTo(i, i-1);
            mPath?.lineTo(width/2.toFloat(), i.toFloat());
            print("value: $i")
        }*/


        val canvas = surface.lockCanvas(null)
        try {
            Log.v(TAG, "drawCircleSurface: isHwAcc=" + canvas.isHardwareAccelerated)
            //canvas.drawRect(100.toFloat(), 100.toFloat(), 1671.toFloat(), 1320.toFloat(), paint);
            //canvas.drawPaint(paint);
            canvas.drawPath(mPath, paint)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }
}
