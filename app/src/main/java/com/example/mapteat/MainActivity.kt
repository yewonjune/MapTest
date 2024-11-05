package com.example.mapteat

import android.Manifest
import android.location.Location
import android.content.pm.PackageManager
import androidx.annotation.NonNull;
import android.os.Bundle
import android.util.Log;
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kakao.vectormap.KakaoMap;
import com.kakao.vectormap.KakaoMapReadyCallback;
import com.kakao.vectormap.MapLifeCycleCallback;
import com.kakao.vectormap.MapView;
import com.kakao.vectormap.LatLng
import com.kakao.sdk.common.util.Utility
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var kakaoMap: KakaoMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 100
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 101
    private var isMapInitialized = false
    private val CHANNEL_ID = "mart_notification_channel"
    private val NOTIFICATION_ID = 1
    private val NEARBY_DISTANCE_THRESHOLD = 50; //사용자의 위치에서 50m 이내에 마트가 있을 경우

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        createNotificationChannel()
        requestNotificationPermission()

        Log.d("KeyHash", "${Utility.getKeyHash(this)}")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        checkAndRequestLocationPermissions()

        mapView = findViewById(R.id.map_view)
        mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {
                Log.d("KakaoMap", "onMapDestroy: 지도 종료")
            }
            override fun onMapError(error: Exception) {
                Log.e("KakaoMap", "onMapError: ", error)
            }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(@NonNull map: KakaoMap) {
                kakaoMap = map
                isMapInitialized = true
                Log.d("KakaoMap", "onMapReady: KakaoMap 준비 완료")

                getCurrentLocationAndShowNearbyMarts()
            }
        })

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun checkAndRequestLocationPermissions() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.d("KakaoMap", "위치 권한이 없음 - 요청 중")
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            Log.d("KakaoMap", "위치 권한 승인됨")
            if (isMapInitialized) {
                getCurrentLocationAndShowNearbyMarts()
            } else {
                Log.e("KakaoMap", "맵이 초기화되지 않음")
            }
        }
    }

    fun getCurrentLocationAndMoveMap() {
        if (isMapInitialized && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        val userLatLng = LatLng.from(it.latitude, it.longitude)
                        val cameraUpdate = CameraUpdateFactory.newCenterPosition(userLatLng)
                        kakaoMap.moveCamera(cameraUpdate)
                        Log.d("KakaoMap", "현재 위치: $userLatLng")
                    }
                }
                .addOnFailureListener {
                    Log.e("KakaoMap", "현재 위치를 가져오는 데 실패했습니다.", it)
                }
        } else {
            Log.e("KakaoMap", "맵이 초기화되지 않았거나 위치 권한이 허용되지 않았습니다.")
        }
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (isMapInitialized) {
                        getCurrentLocationAndMoveMap()
                    }
                } else {
                    Log.e("KakaoMap", "위치 권한이 거부되었습니다.")
                }
            }
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Notification", "알림 권한이 승인되었습니다.")
                } else {
                    Log.e("Notification", "알림 권한이 거부되었습니다.")
                }
            }
        }
    }

    private fun getCurrentLocationAndShowNearbyMarts() {
        if (isMapInitialized && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        val userLatitude = it.latitude
                        val userLongitude = it.longitude
                        val userLatLng = LatLng.from(userLatitude, userLongitude)
                        kakaoMap.moveCamera(CameraUpdateFactory.newCenterPosition(userLatLng))
                        Log.d("KakaoMap", "searchNearbyMarts 호출 전 위치 가져옴")
                        searchNearbyMarts(userLatitude, userLongitude)
                    }
                }
                .addOnFailureListener {
                    Log.e("KakaoMap", "현재 위치를 가져오는 데 실패했습니다.", it)
                }
        } else {
            Log.e("KakaoMap", "맵이 초기화되지 않았거나 위치 권한이 허용되지 않았습니다.")
        }
    }

    private fun searchNearbyMarts(userLatitude: Double, userLongitude: Double) {
        val client = OkHttpClient()
        val url = "https://dapi.kakao.com/v2/local/search/keyword.json?query=마트&x=$userLongitude&y=$userLatitude&radius=2000"
        Log.d("KakaoMap", "마트 검색 URL: $url")
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "KakaoAK d99e7973623316d899b922804c649e61")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("KakaoMap", "마트 검색 실패", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("KakaoMap", "마트 검색 실패: 응답 코드 ${response.code}")
                    return
                }

                response.body?.let { responseBody ->
                    val json = JSONObject(responseBody.string())
                    val documents = json.getJSONArray("documents")

                    for (i in 0 until documents.length()) {
                        val mart = documents.getJSONObject(i)
                        val name = mart.getString("place_name")
                        val martLatitude = mart.getString("y").toDouble()
                        val martLongitude = mart.getString("x").toDouble()

                        Log.d("KakaoMap", "마트 이름: $name, 위치: ($martLatitude, $martLongitude)")

                        val martLocation = Location("").apply {
                            latitude = martLatitude
                            longitude = martLongitude
                        }

                        val userLocation = Location("").apply {
                            latitude = userLatitude
                            longitude = userLongitude
                        }

                        if (userLocation.distanceTo(martLocation) <= NEARBY_DISTANCE_THRESHOLD) {
                            sendMartNotification()
                            break
                        }

                        runOnUiThread {
                            addMartLabel(LatLng.from(martLatitude, martLongitude))
                        }
                    }
                }?: Log.e("KakaoMap", "응답 데이터가 비어 있습니다.")
            }
        })
    }
    private fun addMartLabel(position: LatLng) {
        val styles = kakaoMap.labelManager
            ?.addLabelStyles(LabelStyles.from(LabelStyle.from(R.drawable.mmarker))) // 아이콘없어서 대충그림
        val options = LabelOptions.from(position)
            .setStyles(styles)
        val layer = kakaoMap.labelManager?.getLayer()

        layer?.addLabel(options)
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "Mart Notification"
            val descriptionText = "마트가 50m 이내에 있습니다."
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendMartNotification() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.mmarker) // 아이콘없어서 임시로 아무거나 적용
                .setContentTitle("마트 입장")
                .setContentText("마트에 입장하였습니다!")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            with(NotificationManagerCompat.from(this)) {
                notify(NOTIFICATION_ID, builder.build())
            }
        } else {
            Log.e("Notification", "POST_NOTIFICATIONS 권한이 없습니다.")
            requestNotificationPermission()
        }
    }
}
