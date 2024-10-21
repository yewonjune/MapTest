package com.example.mapteat

import android.app.Application
import com.kakao.sdk.common.KakaoSdk
import com.kakao.vectormap.KakaoMapSdk

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Kakao 지도 SDK 초기화
        //KakaoSdk.init(this, "f9e9925c92af490313eea60bfd943b94")
        KakaoMapSdk.init(this, "f9e9925c92af490313eea60bfd943b94") // 여기에 발급받은 네이티브 앱 키를 넣어주세요
    }
}
