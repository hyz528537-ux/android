package com.tonapps.tonkeeper

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.tonapps.tonkeeper.core.DevSettings
import com.tonapps.tonkeeper.extensions.isDarkMode
import com.tonapps.tonkeeper.os.AppInstall
import com.tonapps.tonkeeper.os.DeviceCountry
import com.tonapps.tonkeeperx.BuildConfig
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import uikit.compose.AppTheme
import java.util.Locale

class Environment(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    data class CountryData(
        val fromStore: String? = null,
        val bySimCard: String? = null,
        val byNetwork: String? = null,
        val byIPAddress: String? = null,
        val debug: String? = null,
    ) {

        private val debugValue: String?
            get() = if (BuildConfig.DEBUG) debug else null

        val value: String?
            get() = (debugValue ?: fromStore ?: bySimCard ?: byNetwork ?: byIPAddress)?.let(::fixCountryCode)
    }

    private val _countryDataFlow = MutableStateFlow<CountryData>(CountryData())
    val countryDataFlow = _countryDataFlow.asStateFlow()
    val countryFlow = _countryDataFlow.asStateFlow().mapNotNull { it.value }.distinctUntilChanged()

    val country: String
        get() = _countryDataFlow.value.value ?: Locale.getDefault().country

    init {
        setDebugCountry(DevSettings.country)
        setCountryBySimCard(DeviceCountry.fromSIM(context))
        setCountryByNetwork(DeviceCountry.fromNetwork(context))
    }

    fun setDebugCountry(country: String?) {
        if (BuildConfig.DEBUG) {
            _countryDataFlow.value = _countryDataFlow.value.copy(debug = country?.uppercase())
        }
    }

    fun setCountryFromStore(country: String?) {
        _countryDataFlow.value = _countryDataFlow.value.copy(fromStore = country?.uppercase())
    }

    fun setCountryBySimCard(country: String?) {
        _countryDataFlow.value = _countryDataFlow.value.copy(bySimCard = country?.uppercase())
    }

    fun setCountryByNetwork(country: String?) {
        _countryDataFlow.value = _countryDataFlow.value.copy(byNetwork = country?.uppercase())
    }

    fun setCountryByIPAddress(country: String?) {
        _countryDataFlow.value = _countryDataFlow.value.copy(byIPAddress = country?.uppercase())
    }

    val theme: AppTheme
        get() {
            return when(settingsRepository.theme.key) {
                "blue" -> AppTheme.BLUE
                "dark" -> AppTheme.DARK
                "light" -> AppTheme.LIGHT
                else -> if (context.isDarkMode) AppTheme.DARK else AppTheme.LIGHT
            }
        }

    val installerSource: AppInstall.Source by lazy { AppInstall.request(context) }

    val isFromGooglePlay: Boolean by lazy {
        installerSource == AppInstall.Source.GOOGLE_PLAY
    }

    val isGooglePlayServicesAvailable: Boolean by lazy {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
        resultCode == ConnectionResult.SUCCESS
    }

    private companion object {
        private fun fixCountryCode(country: String): String {
            val fixedCountry = if (country.length == 2) country.uppercase() else "AE"
            if (BuildConfig.DEBUG) {
                return DevSettings.country ?: fixedCountry
            }
            return fixedCountry
        }
    }
}