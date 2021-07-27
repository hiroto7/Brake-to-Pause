package io.github.hiroto7.braketopause

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import com.google.android.gms.location.DetectedActivity

class MainViewModel(application: Application) : AndroidViewModel(application),
        SharedPreferences.OnSharedPreferenceChangeListener {
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    val speedThreshold = MutableLiveData(sharedPreferences.getInt(application.getString(R.string.speed_threshold_key), 8))

    private val isInVehicleSelected = MutableLiveData(sharedPreferences.getBoolean(application.getString(R.string.in_vehicle_key), true))
    private val isOnBicycleSelected = MutableLiveData(sharedPreferences.getBoolean(application.getString(R.string.on_bicycle_key), true))
    private val isRunningSelected = MutableLiveData(sharedPreferences.getBoolean(application.getString(R.string.running_key), true))
    private val isWalkingSelected = MutableLiveData(sharedPreferences.getBoolean(application.getString(R.string.walking_key), true))

    val selectedActivities = MediatorLiveData<Set<Int>>()

    val usesActivityRecognition = MutableLiveData(sharedPreferences.getBoolean(application.getString(R.string.activity_recognition_key), true))


    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        listOf(isInVehicleSelected, isOnBicycleSelected, isRunningSelected, isWalkingSelected).forEach { liveData ->
            selectedActivities.addSource(liveData) {
                val set = HashSet<Int>()
                if (isInVehicleSelected.value!!) set.add(DetectedActivity.IN_VEHICLE)
                if (isOnBicycleSelected.value!!) set.add(DetectedActivity.ON_BICYCLE)
                if (isRunningSelected.value!!) set.add(DetectedActivity.RUNNING)
                if (isWalkingSelected.value!!) set.add(DetectedActivity.WALKING)
                selectedActivities.value = set
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            getApplication<Application>().getString(R.string.speed_threshold_key) -> speedThreshold.value = sharedPreferences.getInt(getApplication<Application>().getString(R.string.speed_threshold_key), 8)
            getApplication<Application>().getString(R.string.activity_recognition_key) -> usesActivityRecognition.value = sharedPreferences.getBoolean(getApplication<Application>().getString(R.string.activity_recognition_key), true)

            getApplication<Application>().getString(R.string.in_vehicle_key) -> isInVehicleSelected.value = sharedPreferences.getBoolean(getApplication<Application>().getString(R.string.in_vehicle_key), true)
            getApplication<Application>().getString(R.string.on_bicycle_key) -> isOnBicycleSelected.value = sharedPreferences.getBoolean(getApplication<Application>().getString(R.string.on_bicycle_key), true)
            getApplication<Application>().getString(R.string.running_key) -> isRunningSelected.value = sharedPreferences.getBoolean(getApplication<Application>().getString(R.string.running_key), true)
            getApplication<Application>().getString(R.string.walking_key) -> isWalkingSelected.value = sharedPreferences.getBoolean(getApplication<Application>().getString(R.string.walking_key), true)
        }
    }
}