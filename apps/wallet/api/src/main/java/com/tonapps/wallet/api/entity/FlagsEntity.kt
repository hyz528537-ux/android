package com.tonapps.wallet.api.entity

import android.os.Parcelable
import android.util.Log
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class FlagsEntity(
    val disableSwap: Boolean,
    val disableExchangeMethods: Boolean,
    val disableDApps: Boolean,
    val disableBlur: Boolean,
    val disableLegacyBlur: Boolean,
    val disableSigner: Boolean,
    val safeModeEnabled: Boolean,
    val disableStaking: Boolean,
    val disableTron: Boolean,
    val disableBattery: Boolean,
    val disableGasless: Boolean,
    val disableUsde: Boolean,
    val disableNativeSwap: Boolean,
    val disableOnboardingStory: Boolean
) : Parcelable {

    constructor(json: JSONObject) : this(
        disableSwap = json.optBoolean("disable_swap", false),
        disableExchangeMethods = json.optBoolean("disable_exchange_methods", false),
        disableDApps = json.optBoolean("disable_dapps", false),
        disableBlur = json.optBoolean("disable_blur", false),
        disableLegacyBlur = json.optBoolean("disable_legacy_blur", false),
        disableSigner = json.optBoolean("disable_signer", false),
        safeModeEnabled = json.optBoolean("safe_mode_enabled", false),
        disableStaking = json.optBoolean("disable_staking", false),
        disableTron = json.optBoolean("disable_tron", false),
        disableBattery = json.optBoolean("disable_battery", false),
        disableGasless = json.optBoolean("disable_gaseless", false),
        disableUsde = json.optBoolean("disable_usde", false),
        disableNativeSwap = json.optBoolean("disable_native_swap", false),
        disableOnboardingStory = json.optBoolean("disable_onboarding_story", false)
    )

    constructor() : this(
        disableSwap = false,
        disableExchangeMethods = false,
        disableDApps = false,
        disableBlur = false,
        disableLegacyBlur = false,
        disableSigner = false,
        safeModeEnabled = false,
        disableStaking = false,
        disableTron = false,
        disableBattery = false,
        disableGasless = false,
        disableUsde = false,
        disableNativeSwap = false,
        disableOnboardingStory = false
    )
}