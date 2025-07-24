package com.tonapps.tonkeeper.ui.screen.onramp.main

import android.content.Context
import android.text.SpannableStringBuilder
import androidx.core.text.color
import com.tonapps.uikit.color.textSecondaryColor
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.localization.Localization

object OnRampUtils {

    fun createProviderTitle(context: Context, title: String): CharSequence {
        return SpannableStringBuilder(context.getString(Localization.provider))
            .append(" ")
            .color(context.textSecondaryColor) {
                append(title)
            }
    }

    fun fixSymbol(value: String): String {
        if (value.equals("USD₮", ignoreCase = true)) {
            return "USDT"
        }
        return value
    }

    fun resolveNetwork(currency: WalletCurrency): String {
        if (currency.fiat) {
            return "fiat"
        } else if (currency.symbol.equals("TON", ignoreCase = true)) {
            return "native"
        }
        return when (currency.chain.name.lowercase()) {
            "etc", "erc-20" -> "erc-20"
            "ton", "jetton" -> "jetton"
            "tron", "trc-20" -> "trc-20"
            "sol", "spl" -> "spl"
            "bnb", "bep-20" -> "bep-20"
            "avalanche" -> "avalanche"
            "arbitrum" -> "arbitrum"
            else -> "native"
        }
    }
}