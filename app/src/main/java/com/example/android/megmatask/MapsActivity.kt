package com.example.android.megmatask

import android.Manifest
import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.pm.PackageManager
import android.database.MatrixCursor
import android.os.Build
import android.os.Bundle
import android.provider.BaseColumns
import android.util.Log
import android.view.Menu
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import com.example.android.megmatask.databinding.ActivityMapsBinding
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.maps.GeoApiContext
import com.google.maps.PlacesApi
import com.google.maps.model.PlaceType
import com.google.maps.model.PlacesSearchResponse
import com.google.maps.model.RankBy
import java.io.IOException


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    private var mInterstitialAd: InterstitialAd? = null
    private lateinit var currentLatLng: LatLng
    private lateinit var map: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private val FINE_LOCATION_ACCESS_REQUEST_CODE = 1
    private lateinit var fusedLocationClient:FusedLocationProviderClient
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        MobileAds.initialize(this)
        playInterstitialAd()
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)

        // Associate searchable configuration with the SearchView
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        (menu.findItem(R.id.search).actionView as SearchView).apply {
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            addPlacesList()
            onPlaceSelected()

        }

        return true
    }

    private fun SearchView.addPlacesList() {
        val from = arrayOf(SearchManager.SUGGEST_COLUMN_TEXT_1)
        val to = intArrayOf(android.R.id.text1)
        val cursorAdapter = SimpleCursorAdapter(
            this@MapsActivity,
            android.R.layout.simple_list_item_1,
            null,
            from,
            to,
            CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER
        )
        val cursor = MatrixCursor(arrayOf(BaseColumns._ID, SearchManager.SUGGEST_COLUMN_TEXT_1))
        PlacesList.values().forEachIndexed { index, suggestion ->
            cursor.addRow(arrayOf(index, suggestion.title))
        }
        cursorAdapter.changeCursor(cursor)
        suggestionsAdapter = cursorAdapter
        isIconifiedByDefault = false
    }

    private fun SearchView.onPlaceSelected() {
        setOnSuggestionListener(object : SearchView.OnSuggestionListener {
            override fun onSuggestionSelect(position: Int): Boolean {
                return false
            }

            override fun onSuggestionClick(position: Int): Boolean {
                hideKeyboard()
                val selectedPlace = PlacesList.values()[position]
                addMarker(
                    map, selectedPlace.latitude, selectedPlace.longitude, selectedPlace.title
                )
                Toast.makeText(this@MapsActivity, selectedPlace.title, Toast.LENGTH_LONG).show()
                // Do something with selection
                return true
            }
        })
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        enableUserLocation()
        googleMap.setOnMarkerClickListener {
            if(it.position!=currentLatLng)
                 showDirDialog(it.title.toString())
            false
        }

    }


    private fun getNearbyRestaurant(latitude: Double, longitude: Double) {
        var request = PlacesSearchResponse()
        val context  = GeoApiContext.Builder()
            .apiKey(getString(R.string.google_maps_key))
            .build()
        val location = com.google.maps.model.LatLng(latitude, longitude)
        try {
            request = PlacesApi.nearbySearchQuery(context, location)
                .radius(5000)
                .rankby(RankBy.PROMINENCE)
                .keyword("RESTAURANT")
                .language("en")
                .type(PlaceType.RESTAURANT)
                .await()
        } catch (e: ApiException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } finally {
            val result=request.results[0]
            addMarker(map,result.geometry.location.lat,result.geometry.location.lng,result.name)
        }
    }
    private fun addMarker(googleMap: GoogleMap, latitude:Double, longitude:Double, title:String) {
        val markerLatLng = LatLng(latitude, longitude)
        googleMap.addMarker(
            MarkerOptions()
                .position(markerLatLng)
                .title(title)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
        )
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(markerLatLng, 10F))

    }

    private fun showDirDialog(title: String){
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
            // Got last known location. In some rare situations this can be null.
            if (location != null) {
                currentLatLng = LatLng(location.latitude, location.longitude)
                val markerOptions = MarkerOptions().position(currentLatLng)
                map.addMarker(markerOptions)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 10F))
                getNearbyRestaurant(location.latitude,location.longitude)
            }
        }
    }

    private fun enableUserLocation() {
        when {
            (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) -> {
                map.isMyLocationEnabled = true
                getCurrentLocation()
                Toast.makeText(this, "Location permission is granted.", Toast.LENGTH_LONG).show()
            }
            (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_FINE_LOCATION)) ->{
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        FINE_LOCATION_ACCESS_REQUEST_CODE
                    )
                }
            }
            else ->
                requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    FINE_LOCATION_ACCESS_REQUEST_CODE
                )
        }

    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Check if location permissions are granted and if so enable the location
        when (requestCode) {
            FINE_LOCATION_ACCESS_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission is granted. Continue...
                    enableUserLocation()
                } else {
                    Toast.makeText(this, "Location permission was not granted.", Toast.LENGTH_LONG).show()
                }

            }

        }

    }

    private fun hideKeyboard() {
        val inputManager: InputMethodManager =
            getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(
            currentFocus!!.windowToken,
            InputMethodManager.HIDE_NOT_ALWAYS
        )
    }
    private fun playInterstitialAd(){
        var adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            "ca-app-pub-2559564046715323/2204952129",
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d("onAdFailedToLoad ", adError.message)
                    mInterstitialAd = null
                }
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    Log.d("onAdLoaded", "Ad was loaded")
                    mInterstitialAd = interstitialAd
                    mInterstitialAd?.show(this@MapsActivity)
                }
            }
        )
        mInterstitialAd?.fullScreenContentCallback = object: FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d("onAdDismissed", "Ad failed to show")
            }
            override fun onAdFailedToShowFullScreenContent(adError: AdError?) {
                Log.d("onAdFailed", "Ad failed to show.")
            }
            override fun onAdShowedFullScreenContent() {
                Log.d("onAdShowed", "Ad showed fullscreen content")
                mInterstitialAd = null
            }
        }

    }
}