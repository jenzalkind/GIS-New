package com.example.tripy

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation.findNavController
import com.example.tripy.MySingleton.rememberLastCurrentLatLong
import com.example.tripy.databinding.FragmentMainBinding
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.maps.DirectionsApiRequest
import com.google.maps.GeoApiContext
import com.google.maps.internal.PolylineEncoding
import com.google.maps.model.DirectionsResult
import com.google.maps.model.TravelMode
import kotlin.math.roundToInt


object MySingleton {
    lateinit var rememberLastCurrentLatLong: LatLng
}
//problem was here - make sure class insn't abstract
class MainFragment : Fragment(),OnMapReadyCallback,GoogleMap.OnInfoWindowClickListener,GoogleMap.OnPolylineClickListener{
    companion object{ var flag = 0 }
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var binding: FragmentMainBinding
    private lateinit var locationRequest: LocationRequest
    private lateinit var currentLatLong : LatLng
    private lateinit var  description : String
    private lateinit var mMap:GoogleMap
    private lateinit var lastLocation1 : Location // current location vars
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var database : DatabaseReference // firebase realtime database variable
    private var mGeoApiContext : GeoApiContext? = null
    private var fragmentFilterArrayList = ArrayList<String>()   // saving in array list the categories the use chose in FilterAttraction fragment
    private var markerList:ArrayList<Marker> = ArrayList() //Array of selected locations
    private var mPolylinesData:ArrayList<PolylineData> = ArrayList()
    private var mSelectedMarker: Marker? = null
    private var markerListString = ArrayList<String>() //TODO Array of selected string locations
    private val LOCATION_PERMISSION_REQUEST_CODE = 1234
    private val TAG = "MapActivity"
    private val TAG_DB = "Database"
    private val REQUEST_CHECK_SETTINGS = 10001

    //Empty constructor required
    override fun onCreate(savedInstanceState: Bundle?) {
        setHasOptionsMenu(true)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentMainBinding.inflate(layoutInflater)

        firebaseAuth = FirebaseAuth.getInstance()

        if(isGooglePlayServicesAvailable()) {
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireActivity())
            requestLocationPermission()
        }

        return binding.root
    }

    private fun addPolylinesToMap(result: DirectionsResult) {
        Handler(Looper.getMainLooper()).post(Runnable {
            Log.d(TAG, "run: result routes: " + result.routes.size)
            /*if(mPolylinesData.isNotEmpty()){
                for(polylineData in mPolylinesData){
                    polylineData.polyline.remove()
                }
                mPolylinesData.clear()
                mPolylinesData = ArrayList() //check it in case of an error - 8.14
            }*/

            /*var duration = 99999999*/

            for (route in result.routes) {
                Log.d(TAG, "run: leg: " + route.legs[0].toString())
                val decodedPath = PolylineEncoding.decode(route.overviewPolyline.encodedPath)
                val newDecodedPath: MutableList<LatLng> = ArrayList()

                for (latLng in decodedPath) { // This loops through all the LatLng coordinates of ONE polyline.
                    //Log.d(TAG, "run: latlng: " + latLng.toString())
                    newDecodedPath.add(LatLng(latLng.lat, latLng.lng))
                }
                val polyline: Polyline = mMap.addPolyline(PolylineOptions().addAll(newDecodedPath))
                polyline.color = ContextCompat.getColor(activity!!, android.R.color.darker_gray)
                polyline.isClickable = true //select and change color

                mPolylinesData.add(PolylineData(polyline, route.legs[0]))

                onPolylineClick(polyline)

                /*var durationTemp = route.legs[0].duration.inSeconds
                if(durationTemp < duration){
                    duration = durationTemp.toInt()
                    onPolylineClick(polyline)
                }
*/
                mSelectedMarker!!.isVisible = false
            }
        })
    }

    // checking if google services install on the device
    private fun isGooglePlayServicesAvailable(): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val status = googleApiAvailability.isGooglePlayServicesAvailable(requireContext())
        if (status != ConnectionResult.SUCCESS) {
            if (googleApiAvailability.isUserResolvableError(status)) {
                googleApiAvailability.getErrorDialog(requireActivity(), status, 2404)?.show()
            }
            return false
        }
        Log.d(TAG, "isServicesOK: Google Play Services is working")
        return true
    }

    private fun requestLocationPermission() {
        Log.d(TAG, "In requestLocationPermission")
        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION)

        if(ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(),Manifest.permission.ACCESS_FINE_LOCATION))
        {
            android.app.AlertDialog.Builder(requireContext()).setTitle("" +
                    "Location permission denied")
                .setMessage("Location permission required for the app to work properly")
                .setPositiveButton("ok") { dialogInterface, i ->
                    Log.d(TAG, "if was true")
                    requestPermissions(permissions,LOCATION_PERMISSION_REQUEST_CODE)
                }
                .setNegativeButton("cancel") { dialogInterface, i -> dialogInterface.dismiss() }
                .create().show()
        }
        else
        {
            Log.d(TAG, "if was false")
            requestPermissions(permissions,LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Log.d(TAG, "In onRequestPermission")
        if(requestCode == LOCATION_PERMISSION_REQUEST_CODE)
        {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG,"Permission granted")
                checkEnableGps()
            }
            else {
                Log.d(TAG,"Permission denied")
            }
        }
    }

    // This function will call only if they the user enabled Location permission
    private fun checkEnableGps() {
        Log.d(TAG, "checkEnableGps: in function")
        locationRequest = LocationRequest.create()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY // high accuracy location
        locationRequest.interval = 5000
        locationRequest.fastestInterval = 2000

        // Check if the device GPS enable or not
        val builder = LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
        builder.setAlwaysShow(true)

        val result = LocationServices.getSettingsClient(requireActivity())
                .checkLocationSettings(builder.build())
        result.addOnCompleteListener { task ->
            try {
                val response = task.getResult(ApiException::class.java)
                Log.d(TAG, "GPS already on")
                initMap()

                // This e is for the ApiException error in case we get one
            } catch (e: ApiException) {
                when (e.statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> try {
                        // this error means device gps is turned off and open dialog to turn on location. Answer we'll be handle onActivityResult
                        val resolvableApiException = e as ResolvableApiException
                        startIntentSenderForResult(resolvableApiException.resolution.intentSender, REQUEST_CHECK_SETTINGS, null, 0, 0,0,null)
                    } catch (ex: SendIntentException) {
                        ex.printStackTrace()
                    }
                    //Device does not have location so we'll ignore it
                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {}
                }
            }
        }
    }

    // Handle the dialog answer from checkEnableGps()
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    Log.d(TAG, "onActivityResult: GPS ON")
                    initMap()
                }
                Activity.RESULT_CANCELED -> {
                    Log.d(TAG, "onActivityResult: GPS is required  to be turned on ")
                }
            }
        }
    }

    private fun initMap() {
        Log.d(TAG, "initMap: initializing map")
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        //used for calculation directions
        if (mGeoApiContext == null){
            mGeoApiContext = GeoApiContext.Builder()
                .apiKey(getString(R.string.google_maps_key))
                .build()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        Log.d(TAG, "onMapReady: map is ready")
        mMap = googleMap
        mMap.setOnPolylineClickListener(this)
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isMapToolbarEnabled = false
        getDeviceCurrentLocation()

        // if the user doesn't select any filters = the function that shows all the attractions is called
        // else it filters according to the second function with the array of categories he chose that returns from the fragment
        if(flag == 1) {
            fragmentFilterArrayList = retrieveCategoryFilter()
            if(fragmentFilterArrayList.isNotEmpty())
                readDataFromFirebaseByCategory(mMap,fragmentFilterArrayList)
            else
                readDataFromFirebase(mMap)
        }
        /*if(flag == 2){
            Log.d(TAG,"I'm here")
            markerList = retrieveMarkerList()
            if(markerList.isNotEmpty())
                readDataFromFirebaseByCategory(mMap, markerListString)
        }*/
        else
            readDataFromFirebase(mMap)
        mMap.setOnInfoWindowClickListener(this)
    }

    private fun getDeviceCurrentLocation() {
        Log.d(TAG, "getDeviceLocation: getting the devices current location")
        //lateinit var currentLatLong: LatLng

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "getDeviceLocation: no permission")
            return
        }
        mMap.isMyLocationEnabled = true
      //  Log.d(TAG, "getDeviceLocation: $myVariable")
       // rememberLastLocation = fusedLocationProviderClient.getLastLocation()
        fusedLocationProviderClient.lastLocation.addOnSuccessListener(requireActivity()) { location ->
            if(location != null) {
                Log.d(TAG, "getDeviceLocation: In if(location != null)")
                lastLocation1 = location
               // rememberLastLocation = lastLocation
                currentLatLong = LatLng(location.latitude,location.longitude)
                rememberLastCurrentLatLong = currentLatLong
                moveCamera(currentLatLong,11f)
                // lat = location.latitude
            }
            else
                Log.d(TAG, "getDeviceCurrentLocation: current location is null")
        }
    }

    private fun moveCamera(latLng: LatLng, zoom: Float) {
        Log.d(TAG, "moveCamera: moving the camera to: lat: " + latLng.latitude + ", lng: " + latLng.longitude)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng,zoom))
    }

    fun isRTL(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            (context.resources.configuration.layoutDirection === View.LAYOUT_DIRECTION_RTL)
            // Another way:
            // Define a boolean resource as "true" in res/values-ldrtl
            // and "false" in res/values
            // return context.getResources().getBoolean(R.bool.is_right_to_left);
        } else {
            false
        }
    }

    // read the data and add a marker on the map
    private fun readDataFromFirebaseByCategory(googleMap: GoogleMap,categoryFilterArrayList : ArrayList<String>) {
        Log.w(TAG_DB, "In readDataFromFirebaseByCategory")
        lateinit var  distance : String
       // val categoryFilterArrayList: ArrayList<String> = retrieveCategoryFilter()
        googleMap.setInfoWindowAdapter(CustomInfoWindowAdapter(requireContext()))

        database = FirebaseDatabase.getInstance().getReference("Attractions")

        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (dataSnap in dataSnapshot.children) {
                    val hebrewCategory = dataSnap.child("Category").value.toString()
                    val englishCategoryName = dataSnap.child("English Category").value.toString()

                    for (categoryFilter in categoryFilterArrayList) {
                        if (categoryFilter == englishCategoryName || categoryFilter == hebrewCategory) {

                            val hebrewName = dataSnap.child("Hebrew Name").value.toString()

                            val englishAttName = dataSnap.child("Name").value.toString()

                            val latitude = dataSnap.child("latitude").value.toString().toDouble()

                            val longitude = dataSnap.child("Longitude").value.toString().toDouble()
                            //latLngCoordinates.add(LatLng(latitude,longitude))

                            //description = dataSnap.child("description").value.toString()

                            if (::currentLatLong.isInitialized) {
                                //Log.d(TAG_DB, "getDeviceLocation: if = True --> lat = ${currentLatLong.latitude}, ${currentLatLong.longitude}")
                                distance = getDistance(
                                    currentLatLong.latitude,
                                    currentLatLong.longitude,
                                    latitude,
                                    longitude
                                )
                            } else {
                                //Log.d(TAG_DB, "getDeviceLocation: if = false --> lat = ${rememberLastCurrentLatLong.latitude}, ${rememberLastCurrentLatLong.longitude}")
                                currentLatLong = rememberLastCurrentLatLong
                                distance = getDistance(
                                    currentLatLong.latitude,
                                    currentLatLong.longitude,
                                    latitude,
                                    longitude
                                )
                            }
                            // googleMap.setInfoWindowAdapter(CustomInfoWindowAdapter(requireContext()))
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && isRTL(activity!!)) {
                                val snippet =
                                    " שם אטרקציה:  $hebrewName\n קטגוריה:  $hebrewCategory\n מרחק ליעד:  $distance\n"
                                googleMap.addMarker(
                                    MarkerOptions().position(LatLng(latitude, longitude))
                                        .title(hebrewName)
                                        .snippet(snippet))
                            } else {
                                val snippet =
                                    "Attraction Name: $englishAttName\nCategory: $englishCategoryName\nDistance: $distance\n"
                                googleMap.addMarker(
                                    MarkerOptions().position(LatLng(latitude, longitude))
                                        .title(englishAttName)
                                        .snippet(snippet)
                                )
                            }
                        }
                    }
                }
            }
            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                Log.w(TAG_DB, "loadPost:onCancelled", databaseError.toException())
            }
        })
    }

    // retrieve the specific categories the user chose from the fragment FilterAttraction
    //TODO -> use this after clicking on "Build Route"
    private fun retrieveCategoryFilter() : ArrayList<String> {
        var retrieveCategoryArrayList = ArrayList<String>()
        if (flag == 1) { // in case the user did not pick any category filter (all checkboxes are empty)
            retrieveCategoryArrayList = requireArguments().getStringArrayList("categoryFilerKey") as ArrayList<String>
            return retrieveCategoryArrayList
        }
        return retrieveCategoryArrayList
        }

    // retrieve the selected locations from array
    private fun retrieveMarkerList(markers : ArrayList<Marker>) {

        for (i in markers.indices-1){
            calculateDirections(markers[i],markers[i+1])
        }

        calculateDirections(markerList[0], markerList[1])

        //selectedLocations
        //var markerList = ArrayList<Marker>()
        /*if(flag == 2){
            markerList = requireArguments().getStringArrayList(id.toString()) as ArrayList<Marker>
            return markerList
        }
        return markerList*/
    } //TODO

    private fun readDataFromFirebase(googleMap: GoogleMap) {
        lateinit var  distance : String
        Log.w(TAG_DB, "In readDataFromFirebase")
        googleMap.setInfoWindowAdapter(CustomInfoWindowAdapter(requireContext()))

        database = FirebaseDatabase.getInstance().getReference("Attractions")
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (dataSnap in dataSnapshot.children) {
                    val category = dataSnap.child("Category").value.toString()
                    val englishCategoryName = dataSnap.child("English Category").value.toString()

               //     if (selectedCategory == englishCategoryName) {
                  //      count++
                       // Log.w(TAG_DB, "$count")
                        val hebrewName = dataSnap.child("Hebrew Name").value.toString()
                        val englishAttName = dataSnap.child("Name").value.toString()
                        val latitude = dataSnap.child("latitude").value.toString().toDouble()
                        val longitude = dataSnap.child("Longitude").value.toString().toDouble()
                        //latLngCoordinates.add(LatLng(latitude,longitude))

                      //  description = dataSnap.child("description").value.toString()

                    if (::currentLatLong.isInitialized) {
                        //Log.d(TAG_DB, "getDeviceLocation: if = True --> lat = ${currentLatLong.latitude}, ${currentLatLong.longitude}")
                        distance = getDistance(currentLatLong.latitude, currentLatLong.longitude, latitude, longitude)
                    } else {
                        //Log.d(TAG_DB, "getDeviceLocation: if = false --> lat = ${rememberLastCurrentLatLong.latitude}, ${rememberLastCurrentLatLong.longitude}")
                        currentLatLong = rememberLastCurrentLatLong
                        distance = getDistance(currentLatLong.latitude, currentLatLong.longitude, latitude, longitude)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && isRTL(activity!!)) {
                        val snippet = " שם אטרקציה:  $hebrewName\n קטגוריה:  $category\n מרחק ליעד:  $distance\n"
                        googleMap.addMarker(MarkerOptions().position(LatLng(latitude, longitude))
                            .title(hebrewName)
                            .snippet(snippet))
                    } else {
                        val snippet = "Attraction Name: $englishAttName\nCategory: $englishCategoryName\nDistance: $distance\n"
                        googleMap.addMarker(MarkerOptions().position(LatLng(latitude, longitude))
                            .title(englishAttName)
                            .snippet(snippet))
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Post failed, log a message
                Log.w(TAG_DB, "loadPost:onCancelled", databaseError.toException())
            }
        })
    }

    //private fun calculateDirections(marker: Marker)
    private fun calculateDirections(origMarker: Marker, destMarker: Marker) { // הפונקציה תקבל עוד מרקר ותשתמש בו למרקר מקור
        Log.d(TAG, "calculateDirections: Started calculating directions.")

        val dest = com.google.maps.model.LatLng(destMarker.position.latitude, destMarker.position.longitude)
        Log.d(TAG, "calculateDirections: The destination is: $dest")

        val directions = DirectionsApiRequest(mGeoApiContext)
        directions.origin(com.google.maps.model.LatLng(origMarker.position.latitude, origMarker.position.longitude)) //required
        directions.destination(com.google.maps.model.LatLng(destMarker.position.latitude, destMarker.position.longitude)) //required
        directions.mode(TravelMode.DRIVING) //required
        directions.alternatives(true) //showing all possible routes (optional)

        //directions.optimizeWaypoints(true) // THIS IS WHAT I NEED - showing optimal route

        //Log.d(TAG,"calculateDirections: Current locations is: $currentLatLong")

        //callback needs to be called after the request id completed
        //directions.destination(destination).setCallback(PendingResult.Callback<DirectionsResult?> {
        //fun onResult(result: DirectionsResult, callback: PendingResult<DirectionsResult>) { //retrieving info

        directions.setCallback(object : com.google.maps.PendingResult.Callback<DirectionsResult>{
            override fun onResult(result: DirectionsResult) { //retrieving info
                Log.d(TAG, "calculateDirections1: routes: " + result.routes[0].toString())
                Log.d(TAG, "calculateDirections2: duration: " + result.routes[0].legs[0].duration)
                Log.d(TAG, "calculateDirections3: distance: " + result.routes[0].legs[0].distance)
                Log.d(TAG, "calculateDirections4: geocodedWayPoints: " + result.geocodedWaypoints[0].toString())

                addPolylinesToMap(result)
            }
            override fun onFailure(e: Throwable) {
                //Log.d(TAG,"calculateDirections: Current locations is: $currentLatLong")
                Log.e(TAG, "calculateDirections: Failed to get directions: " + e.message)
            }
        })
    }

    override fun onInfoWindowClick(p0: Marker) {
        Log.d("test","testing info window")
        val builder: AlertDialog.Builder = AlertDialog.Builder(activity)
        builder.setMessage(p0.getSnippet())
            .setCancelable(true)
            .setPositiveButton(getString(R.string.addToRoute),
                DialogInterface.OnClickListener { dialog, id ->
                    mSelectedMarker = p0
                    markerList.add(p0)
                    dialog.dismiss()
                    Log.d(TAG, "MarkerList - Added to Array, Array size is ${markerList.size}")})
            .setNeutralButton(getString(R.string.moreInfo),
                DialogInterface.OnClickListener { dialog, id ->
                    onInfoClick(p0)
                    dialog.dismiss()})
            .setNegativeButton(getString(R.string.cancel),
                DialogInterface.OnClickListener { dialog, id -> dialog.cancel() })
        val alert: AlertDialog = builder.create()
        alert.show()

        p0.hideInfoWindow()

        p0.position.latitude //TODO IMPORTANT
    }

    private fun getDistance(startLat: Double, startLon: Double, endLat: Double, endLon: Double): String {
        val results = FloatArray(1)
        val roundOff : Float
        val distanceInKm : Float
        Location.distanceBetween(startLat, startLon, endLat, endLon, results)

        if(results[0]>1000) {
            distanceInKm = results[0] / 1000
            roundOff = ((distanceInKm * 100.0).roundToInt() / 100.0).toFloat()
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && isRTL(activity!!))
                "$roundOff קילומטר "
            else
                "$roundOff kilometers"
        }
        else {
            distanceInKm = results[0] / 1000
            roundOff = ((distanceInKm * 100.0).roundToInt() / 100.0).toFloat()
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && isRTL(activity!!))
                "$roundOff מטר "
            else
                "$roundOff meters"
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.drawer_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.logout -> { // navigate to settings screen
                firebaseAuth.signOut()
                findNavController(binding.root).navigate(R.id.action_mainFragment_to_loginFragment)
                flag = 0
                true
            }
            R.id.helpus -> {
                findNavController(binding.root).navigate(R.id.action_mainFragment_to_help_us_improve)
                flag = 0
                return true
            }
            R.id.update -> {
                findNavController(binding.root).navigate(R.id.action_mainFragment_to_fragment_keep_us_posted)
                flag = 0
                return true
            }
            R.id.filter -> {
                flag = 1
                Log.w("filter", "in tafrit flag = $flag")
                findNavController(binding.root).navigate(R.id.action_mainFragment_to_filtterAttraction)
                return true
            }
            R.id.build -> {
                //flag = 2
                Log.d(TAG, "Build route is selected by user")
                if(markerList.isEmpty() || markerList.size == 1){
                    Log.d(TAG, "MarkerList is empty or not complete - user needs to add (more) locations")
                    val alertDialog: AlertDialog.Builder = AlertDialog.Builder(activity)
                    alertDialog.setMessage(getString(R.string.emptyMarkerList))
                    val alert: AlertDialog = alertDialog.create()
                    alert.show()
                }
                else{
                    //for (i in markerList.indices){
                    //mSelectedMarker = markerList[1]

                    var index = markerList.size-2
                    for (i in 0..index){
                        calculateDirections(markerList[i],markerList[i+1])
                    }



                    //calculateDirections(markerList[0], markerList[1])
                }
                return true
            }
            R.id.clear -> {
                if(markerList.isNotEmpty()){
                    markerList.clear()
                    for(element in markerList) {
                        Log.d("MarkerList","$element")
                    }
                    //Log.d("MarkerList",markerList)
                }
                for(polylineData in mPolylinesData){
                    polylineData.polyline.remove()
                }
                mSelectedMarker!!.isVisible = true
                Log.d(TAG, "MarkerList is clear")
                val alertDialog: AlertDialog.Builder = AlertDialog.Builder(activity)
                alertDialog.setMessage(getString(R.string.clearRouteMessage))
                val alert: AlertDialog = alertDialog.create()
                alert.show()
                return true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPolylineClick(polyline: Polyline) {
        //polyline.color = ContextCompat.getColor(activity!!, R.color.blue)
        //polyline.zIndex = 1f

        var tripNum = 0
        for (polylineData in mPolylinesData) {
            tripNum++
            Log.d(TAG, "onPolylineClick: toString: $polylineData")
            if (polyline.id.equals(polylineData.polyline.id)) {
                polylineData.polyline.color = ContextCompat.getColor(activity!!, R.color.blue)
                polylineData.polyline.zIndex = 1f

                var endLocation = LatLng(polylineData.leg.endLocation.lat, polylineData.leg.endLocation.lng) //planting a new marker
                val marker = mMap.addMarker(MarkerOptions()
                    .position(endLocation)
                    .title("Trip: #$tripNum")
                    .snippet("Duration: " + polylineData.leg.duration)
                )

                if (marker != null) {
                    marker.showInfoWindow()
                }

                /*mMap.addMarker(MarkerOptions()
                        .position(endLocation)
                        .title("Trip: #$tripNum")
                        .snippet("Duration: " + polylineData.leg.duration)
                )?.showInfoWindow()*/


            } else {
                polylineData.polyline.color = ContextCompat.getColor(activity!!, android.R.color.darker_gray)
                polylineData.polyline.zIndex = 0f
            }
        }
    }

    fun onInfoClick(p0: Marker) {
        var lat : Double
        var lon: Double
        val realTimeDatabase = FirebaseDatabase.getInstance().getReference("Attractions")
        realTimeDatabase.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                for (dataSnap in dataSnapshot.children) {
                    val description = dataSnap.child("description").value.toString()
                    lat = dataSnap.child("latitude").value.toString().toDouble()
                    lon = dataSnap.child("Longitude").value.toString().toDouble()

                    // Log.w(TAG_DB, "onInfoWindowClick: In function, description = ${p0.title})" )

                    if(p0.position.latitude == lat && p0.position.longitude == lon) {
                        Log.w(TAG_DB, "onInfoWindowClick: In function, description = ${p0.title})" )
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(changeBlogUrlByLanguage(description)))
                        startActivity(intent)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG_DB, "onInfoWindowClick: in onCancelled, Error = ${error})" )

            }
        })
    }

    private fun changeBlogUrlByLanguage(attrBlogUrl: String) : String {
        val newBlogUrl:String
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && isRTL(activity!!)) { // התנאי הזה זה השינויים שצריך לעשות במידה והטלפון בעברית
            return if(!attrBlogUrl.contains("/he/")) {
                newBlogUrl = attrBlogUrl.replace(".com/", ".com/he/")
                newBlogUrl
            } else
                attrBlogUrl
        }
        else {
            return if (attrBlogUrl.contains("/he/")) {
                newBlogUrl = attrBlogUrl.replace("/he/", "/")
                newBlogUrl
            } else
                attrBlogUrl
        }
    }
}