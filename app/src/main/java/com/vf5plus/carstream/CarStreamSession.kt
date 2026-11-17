package com.vf5plus.carstream

import androidx.car.app.Screen
import androidx.car.app.Session

class CarStreamSession : Session() {

    override fun onCreateScreen(intent: android.content.Intent): Screen {
        return CarStreamScreen(carContext)
    }
}
