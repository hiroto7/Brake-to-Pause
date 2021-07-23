package io.github.hiroto7.braketopause

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager

class MainViewModel(application: Application) : AndroidViewModel(application), SharedPreferences.OnSharedPreferenceChangeListener {
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    val speedThreshold = MutableLiveData(sharedPreferences.getInt(application.getString(R.string.speed_threshold_key), 8))

    val inVehicle = MutableLiveData(sharedPreferences.getBoolean(application.getString(R.string.in_vehicle_key), true))
    val onBicycle = MutableLiveData(sharedPreferences.getBoolean(application.getString(R.string.on_bicycle_key), true))
    val running = MutableLiveData(sharedPreferences.getBoolean(application.getString(R.string.running_key), true))
    val walking = MutableLiveData(sharedPreferences.getBoolean(application.getString(R.string.walking_key), true))

    val selectedActivityCount = MediatorLiveData<Int>()

    val usesActivityRecognition = MutableLiveData(sharedPreferences.getBoolean(application.getString(R.string.activity_recognition_key), true))

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        val liveDataList = listOf(inVehicle, onBicycle, running, walking)
        val observer = Observer<Boolean> {
            selectedActivityCount.value = liveDataList.filter { it.value!! }.size
        }
        liveDataList.forEach { selectedActivityCount.addSource(it, observer) }
    }

    override fun onCleared() {
        super.onCleared()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == getApplication<Application>().getString(R.string.speed_threshold_key)) {
            speedThreshold.value = sharedPreferences.getInt(getApplication<Application>().getString(R.string.speed_threshold_key), 8)
        }

        if (key == getApplication<Application>().getString(R.string.activity_recognition_key)) {
            usesActivityRecognition.value = sharedPreferences.getBoolean(getApplication<Application>().getString(R.string.activity_recognition_key), true)
        }

        if (key == getApplication<Application>().getString(R.string.in_vehicle_key)) {
            inVehicle.value = sharedPreferences.getBoolean(getApplication<Application>().getString(R.string.in_vehicle_key), true)
        }
        if (key == getApplication<Application>().getString(R.string.on_bicycle_key)) {
            onBicycle.value = sharedPreferences.getBoolean(getApplication<Application>().getString(R.string.on_bicycle_key), true)
        }
        if (key == getApplication<Application>().getString(R.string.running_key)) {
            running.value = sharedPreferences.getBoolean(getApplication<Application>().getString(R.string.running_key), true)
        }
        if (key == getApplication<Application>().getString(R.string.walking_key)) {
            walking.value = sharedPreferences.getBoolean(getApplication<Application>().getString(R.string.walking_key), true)
        }
    }
}