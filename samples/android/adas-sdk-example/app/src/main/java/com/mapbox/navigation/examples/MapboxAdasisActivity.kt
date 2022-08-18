package com.mapbox.navigation.examples

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.android.core.permissions.PermissionsManager.areLocationPermissionsGranted
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.extension.observable.eventdata.MapLoadingErrorEventData
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.delegates.listeners.OnMapLoadErrorListener
import com.mapbox.maps.plugin.gestures.OnMapLongClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.options.RoutingTilesOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.replay.MapboxReplayer
import com.mapbox.navigation.core.replay.ReplayLocationEngine
import com.mapbox.navigation.core.replay.history.ReplayEventLocation
import com.mapbox.navigation.core.replay.history.ReplayEventUpdateLocation
import com.mapbox.navigation.core.replay.route.ReplayProgressObserver
import com.mapbox.navigation.core.replay.route.ReplayRouteMapper
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.examples.databinding.LayoutActivityAdasisBinding
import com.mapbox.navigation.ui.maps.NavigationStyles
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.RouteLayerConstants
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowApi
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions
import com.mapbox.navigation.ui.maps.route.line.model.NavigationRouteLine
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineResources
import com.mapbox.navigator.ADASISv2Message
import com.mapbox.navigator.ADASISv2MessageCallback
import com.mapbox.navigator.AdasisConfigBuilder
import java.io.File
import java.lang.ref.WeakReference
import java.util.*
import kotlin.collections.ArrayDeque


class AdasisActivity : AppCompatActivity(), PermissionsListener, OnMapLongClickListener {

    private companion object {
        private const val LOG_TAG = "ADASIS_Activity"
        private const val EPS = 0.03
    }

    private val permissionsManager = PermissionsManager(this)

    private val mapboxReplayer = MapboxReplayer()

    private val replayProgressObserver = ReplayProgressObserver(mapboxReplayer)
    private val replayRouteMapper = ReplayRouteMapper()

    private val viewBinding: LayoutActivityAdasisBinding by lazy {
        LayoutActivityAdasisBinding.inflate(layoutInflater)
    }

    private val mapboxMap: MapboxMap by lazy {
        viewBinding.mapView.getMapboxMap()
    }

    private val navigationLocationProvider by lazy {
        NavigationLocationProvider()
    }

    private val locationComponent by lazy {
        viewBinding.mapView.location.apply {
            setLocationProvider(navigationLocationProvider)
            enabled = true
        }
    }

    private val mapboxNavigation by lazy {
        if (MapboxNavigationProvider.isCreated()) {
            MapboxNavigationProvider.retrieve()
        } else {
            MapboxNavigationProvider.create(
                NavigationOptions.Builder(this)
                    .routingTilesOptions(
                        RoutingTilesOptions.Builder()
                            .tilesVersion("2022_08_06-03_00_00")
                            .build()
                    )
                    .accessToken(getMapboxAccessTokenFromResources())
                    .locationEngine(ReplayLocationEngine(mapboxReplayer))
                    .build()
            )
        }
    }

    private val routeLineColorResources by lazy {
        RouteLineColorResources.Builder()
            .routeLineTraveledColor(Color.LTGRAY)
            .routeLineTraveledCasingColor(Color.GRAY)
            .build()
    }

    private val routeLineResources: RouteLineResources by lazy {
        RouteLineResources.Builder()
            .routeLineColorResources(routeLineColorResources)
            .build()
    }

    private val options: MapboxRouteLineOptions by lazy {
        MapboxRouteLineOptions.Builder(this)
            .withRouteLineResources(routeLineResources)
            .withRouteLineBelowLayerId("road-label-navigation")
            .withVanishingRouteLineEnabled(true)
            .build()
    }

    private val routeLineView by lazy {
        MapboxRouteLineView(options)
    }

    private val routeLineApi: MapboxRouteLineApi by lazy {
        MapboxRouteLineApi(options)
    }

    private val routeArrowApi: MapboxRouteArrowApi by lazy {
        MapboxRouteArrowApi()
    }

    private val routeArrowOptions by lazy {
        RouteArrowOptions.Builder(this)
            .withAboveLayerId(RouteLayerConstants.TOP_LEVEL_ROUTE_LINE_LAYER_ID)
            .build()
    }

    private val routeArrowView: MapboxRouteArrowView by lazy {
        MapboxRouteArrowView(routeArrowOptions)
    }

    private val routesObserver: RoutesObserver = RoutesObserver { routeUpdateResult ->
        val routeLines = routeUpdateResult.navigationRoutes.map { NavigationRouteLine(it, null) }

        routeLineApi.setNavigationRouteLines(
            routeLines
        ) { value ->
            mapboxMap.getStyle()?.apply {
                routeLineView.renderRouteDrawData(this, value)
            }
        }
    }

    private val onPositionChangedListener = OnIndicatorPositionChangedListener { point ->
        val result = routeLineApi.updateTraveledRouteLine(point)
        mapboxMap.getStyle()?.apply {
            routeLineView.renderRouteLineUpdate(this, result)
        }
    }

    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        if (routeProgress.distanceRemaining > EPS) {
            routeLineApi.updateWithRouteProgress(routeProgress) { result ->
                mapboxMap.getStyle()?.apply {
                    routeLineView.renderRouteLineUpdate(this, result)
                }
            }

            val arrowUpdate = routeArrowApi.addUpcomingManeuverArrow(routeProgress)
            mapboxMap.getStyle()?.apply {
                routeArrowView.renderManeuverUpdate(this, arrowUpdate)
            }
        } else {
            stopSimulation()
        }
    }

    private val adasisObserver = object : ADASISv2MessageCallback {
        private val adasisMsgRender by lazy {
            AdasisMsgRender(viewBinding)
        }
        val messages = ArrayList<ADASISv2Message>()

        override fun run(message: ADASISv2Message) {
            messages.add(message)
            adasisMsgRender.render(message.toHex())
        }

        fun reset() {
            messages.clear()
            adasisMsgRender.clear()
        }
    }

    private val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: Location) {}
        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            navigationLocationProvider.changePosition(
                locationMatcherResult.enhancedLocation,
                locationMatcherResult.keyPoints,
            )
            viewportDataSource.onLocationChanged(locationMatcherResult.enhancedLocation)
            viewportDataSource.evaluate()
        }
    }

    private lateinit var navigationCamera: NavigationCamera

    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource

    private val pixelDensity = Resources.getSystem().displayMetrics.density

    private val overviewPadding: EdgeInsets by lazy {
        EdgeInsets(
            140.0 * pixelDensity,
            40.0 * pixelDensity,
            120.0 * pixelDensity,
            40.0 * pixelDensity
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isMapboxTokenProvided()) {
            showNoTokenErrorDialog()
            return
        }

        if (areLocationPermissionsGranted(this)) {
            requestStoragePermission()
        } else {
            permissionsManager.requestLocationPermissions(this)
        }

        showManual()

        setContentView(viewBinding.root)
        init()
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        Toast.makeText(
            this,
            "This app needs location and storage permissions in order to show its functionality.",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            requestStoragePermission()
        } else {
            Toast.makeText(
                this,
                "You didn't grant the permissions required to use the app",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun requestStoragePermission() {
        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        val permissionsNeeded: MutableList<String> = ArrayList()
        if (
            ContextCompat.checkSelfPermission(this, permission) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(permission)
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                10
            )
        }
    }

    //    Put your public access token into mapbox_access_token.xml
    //    <?xml version="1.0" encoding="utf-8"?>
    //    <resources>
    //        <string name="mapbox_access_token"></string>
    //    </resources>
    private fun isMapboxTokenProvided() = getString(R.string.mapbox_access_token).isNotEmpty()

    private fun showNoTokenErrorDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.noTokenDialogTitle))
            .setMessage(getString(R.string.noTokenDialogBody))
            .setCancelable(false)
            .setPositiveButton("Ok") { _, _ ->
                finish()
            }
            .show()
    }

    private fun showManual() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.manualTitle))
            .setMessage(getString(R.string.manualBody))
            .setCancelable(false)
            .setPositiveButton("Ok") { _, _ -> }
            .show()
    }

    private fun init() {
        initStyle()
        initNavigation()
        initListeners()
        initCamera()

        locationComponent.locationPuck = LocationPuck2D(
            null,
            ContextCompat.getDrawable(
                this@AdasisActivity,
                R.drawable.puck2
            ),
            null,
            null
        )

    }

    @SuppressLint("MissingPermission")
    private fun initStyle() {
        mapboxMap.loadStyleUri(
            NavigationStyles.NAVIGATION_NIGHT_STYLE, {
                mapboxNavigation.navigationOptions.locationEngine.getLastLocation(
                    locationEngineCallback
                )
                viewBinding.mapView.gestures.addOnMapLongClickListener(this)
            },
            object : OnMapLoadErrorListener {
                override fun onMapLoadError(eventData: MapLoadingErrorEventData) {
                    Log.e(
                        AdasisActivity::class.java.simpleName,
                        "Error loading map - error type: " +
                                "${eventData.type}, message: ${eventData.message}"
                    )
                }
            }
        )
    }

    @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
    @SuppressLint("MissingPermission")
    private fun initNavigation() {
        mapboxNavigation.run {
            registerLocationObserver(locationObserver)
            registerRouteProgressObserver(replayProgressObserver)
            registerRoutesObserver(routesObserver)
        }
        setFirstPoint(11.581980, 48.135125)
    }

    private fun setFirstPoint(lng: Double, lat: Double) {
        val event = ReplayEventUpdateLocation(
            1656608253.0,
            ReplayEventLocation(lng, lat, null, null, null, null, null, null)
        )
        mapboxReplayer.pushEvents(listOf(event))
        mapboxReplayer.playbackSpeed(1.5)
        mapboxReplayer.play()
    }

    override fun onMapLongClick(point: Point): Boolean {
        val currentLocation = navigationLocationProvider.lastLocation
        if (currentLocation != null) {
            val originPoint = Point.fromLngLat(
                currentLocation.longitude,
                currentLocation.latitude
            )
            findRoute(originPoint, point)
        }
        return false
    }

    private fun findRoute(origin: Point?, destination: Point?) {
        val routeOptions = RouteOptions.builder()
            .applyDefaultNavigationOptions()
            .applyLanguageAndVoiceUnitOptions(this)
            .coordinatesList(listOf(origin, destination))
            .layersList(listOf(mapboxNavigation.getZLevel(), null))
            .alternatives(true)
            .build()
        mapboxNavigation.requestRoutes(
            routeOptions,
            object : NavigationRouterCallback {
                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    routerOrigin: RouterOrigin
                ) {
                    if (routes.isNotEmpty()) {
                        mapboxNavigation.setNavigationRoutes(listOf(routes.first()))
                        Log.i(LOG_TAG, "routes fetched successfully")
                    } else {
                        Log.i(LOG_TAG, "There are no routes")
                    }
                }

                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {}
                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    Log.e(LOG_TAG, "route fetch failed")
                }
            }
        )
    }

    private fun getMapboxAccessTokenFromResources(): String {
        return getString(this.resources.getIdentifier("mapbox_access_token", "string", packageName))
    }

    @SuppressLint("SetTextI18n", "MissingPermission")
    private fun initListeners() {
        viewBinding.runAdasis.setOnClickListener {
            val route = mapboxNavigation.getNavigationRoutes().firstOrNull()
            if (route != null) {
                mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
                mapboxNavigation.startTripSession()
                locationComponent.addOnIndicatorPositionChangedListener(onPositionChangedListener)

                mapboxMap.getStyle()?.apply {
                    routeLineView.hideAlternativeRoutes(this)
                }

                startSimulation(route)
            }
        }
    }

    private fun initCamera() {
        viewportDataSource = MapboxNavigationViewportDataSource(mapboxMap)
        viewportDataSource.overviewPadding = overviewPadding
        viewportDataSource.options.followingFrameOptions.defaultPitch = 0.0

        val displayMetrics = applicationContext.resources.displayMetrics
        val pixelDensity = displayMetrics.density
        viewportDataSource.followingPadding = EdgeInsets(
            0.0 * pixelDensity,
            0.0 * pixelDensity,
            displayMetrics.heightPixels / 3.0,
            0.0 * pixelDensity
        )
        navigationCamera = NavigationCamera(
            mapboxMap,
            viewBinding.mapView.camera,
            viewportDataSource
        )

        viewBinding.mapView.camera.addCameraAnimationsLifecycleListener(
            NavigationBasicGesturesHandler(navigationCamera)
        )
    }

    override fun onStart() {
        super.onStart()
        mapboxNavigation.registerLocationObserver(locationObserver)
        mapboxNavigation.registerRouteProgressObserver(replayProgressObserver)
        mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.registerRoutesObserver(routesObserver)
    }

    override fun onStop() {
        super.onStop()
        locationComponent.removeOnIndicatorPositionChangedListener(onPositionChangedListener)
        mapboxNavigation.unregisterRouteProgressObserver(replayProgressObserver)
        mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.unregisterLocationObserver(locationObserver)
        mapboxNavigation.unregisterRoutesObserver(routesObserver)
    }

    @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
    @SuppressLint("MissingPermission")
    private fun startSimulation(route: NavigationRoute) {
        adasisObserver.reset()

        mapboxNavigation.experimental.setAdasisMessageCallback(
            adasisObserver,
            AdasisConfigBuilder.defaultOptions()
        )

        val replayData = replayRouteMapper.mapDirectionsRouteGeometry(route.directionsRoute)
        mapboxReplayer.run {
            stop()
            clearEvents()
            pushEvents(replayData)
            seekTo(replayData[0])
            play()
        }
        viewBinding.runAdasis.text = "Running ADASISv2..."

        navigationCamera.requestNavigationCameraToIdle()
        viewportDataSource.onRouteChanged(route)
        viewportDataSource.evaluate()
        navigationCamera.requestNavigationCameraToFollowing()
    }

    private fun saveAdasis() {
        val timestamp = Calendar.getInstance().timeInMillis
        val documents =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val path = File(documents, "/mapbox/adasis/$timestamp")
        if (!path.exists()) {
            path.mkdirs()
        }
        Toast.makeText(applicationContext, "Saving to '$path'", Toast.LENGTH_SHORT).show()

        val route = mapboxNavigation.getNavigationRoutes().firstOrNull()
        if (route != null) {
            File(path, "/route.json").printWriter().use { out ->
                out.print(route.directionsRoute.toJson())
            }
        }

        val messages = adasisObserver.messages
        val output = messages.joinToString(",", "[", "]") {
            "[\"${it.toHex()}\", ${it.toJson()}]"
        }
        File(path, "/messages.json").printWriter().use { out -> out.print(output) }
    }

    @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
    private fun stopSimulation() {
        mapboxNavigation.experimental.resetAdasisMessageCallback()
        saveAdasis()

        viewBinding.runAdasis.text = "Run ADASISv2"

        locationComponent.removeOnIndicatorPositionChangedListener(onPositionChangedListener)
        mapboxNavigation.stopTripSession()
        mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
        viewportDataSource.clearRouteData()
        viewportDataSource.evaluate()
        navigationCamera.requestNavigationCameraToOverview()
    }

    override fun onDestroy() {
        super.onDestroy()
        routeLineApi.cancel()
        routeLineView.cancel()
        mapboxReplayer.finish()
        mapboxNavigation.onDestroy()
    }

    private val locationEngineCallback = MyLocationEngineCallback(this)

    private class MyLocationEngineCallback(activity: AdasisActivity) :
        LocationEngineCallback<LocationEngineResult> {

        private val activityRef: WeakReference<AdasisActivity> by lazy {
            WeakReference(activity)
        }

        override fun onSuccess(result: LocationEngineResult?) {
            val location = result?.lastLocation
            val activity = activityRef.get()
            if (location != null && activity != null) {
                val point = Point.fromLngLat(location.longitude, location.latitude)
                val cameraOptions = CameraOptions.Builder().center(point).zoom(14.0).build()

                activity.mapboxMap.setCamera(cameraOptions)
                activity.navigationLocationProvider.changePosition(location, listOf(), null, null)
            }
        }

        override fun onFailure(exception: Exception) {
            Log.e(LOG_TAG, "$exception")
        }
    }

    private class AdasisMsgRender(var viewBinding: LayoutActivityAdasisBinding) {
        private companion object {
            private const val MAX_SIZE = 30 // chosen empirically on 5.6' screen
        }

        private val messages = ArrayDeque<String>(MAX_SIZE)

        fun render(message: String) {
            messages.addLast(message)
            if (messages.size >= MAX_SIZE) {
                messages.removeFirst()
            }
            viewBinding.adasisMsgs.text = messages.joinToString("\n")
        }

        fun clear() {
            messages.clear()
        }
    }
}
