package com.tonapps.wallet.data.rates.entity

import android.os.Parcelable
import android.util.Log
import com.tonapps.icu.Coins
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

@Parcelize
data class RatesEntity(
    val currency: WalletCurrency,
    private val map: Map<String, RateEntity>
): Parcelable {

    companion object {

        fun empty(currency: WalletCurrency): RatesEntity {
            return RatesEntity(currency, hashMapOf())
        }
    }

    val isEmpty: Boolean
        get() = map.isEmpty()

    private val isUSD: Boolean
        get() = currency.code == "USD"

    val currencyCode: String
        get() = currency.code

    val tokens: List<String>
        get() = map.keys.toList()

    fun hasToken(token: String): Boolean {
        return map.containsKey(token)
    }

    fun hasTokens(tokens: List<String>): Boolean {
        for (token in tokens) {
            if (!hasToken(token)) {
                return false
            }
        }
        return true
    }

    fun merge(rates: List<RateEntity>): RatesEntity {
        val newMap = map.toMutableMap()
        for (rate in rates) {
            newMap[rate.tokenCode] = rate.copy()
        }
        return copy(map = newMap.toMap())
    }

    fun filter(tokens: List<String>): RatesEntity {
        val result = hashMapOf<String, RateEntity>()
        for (token in tokens) {
            val rate = map[token] ?: continue
            result[token] = rate
        }
        return RatesEntity(currency, result)
    }

    fun rate(token: String): RateEntity? {
        return map[token]
    }

    fun rateValue(token: String): Coins {
        return rate(token)?.value ?: Coins.ZERO
    }

    fun rateDiff(token: String): RateDiffEntity? {
        return rate(token)?.diff
    }

    fun convertTON(value: Coins): Coins {
        return convert(TokenEntity.TON.address, value)
    }

    fun convert(
        from: WalletCurrency,
        value: Coins,
        to: WalletCurrency
    ): Coins {
        if (from == to) {
            return value
        }
        if (from.isUSDT && to == WalletCurrency.USD || from == WalletCurrency.USD && to.isUSDT) {
            return value
        }
        if (!value.isPositive || isEmpty || (from.fiat && to.fiat)) {
            return Coins.ZERO
        }
        return if (from.fiat) {
            convertFromFiat(to.address, value)
        } else if (to.fiat) {
            convert(from.address, value)
        } else {
            convertJetton(from, value, to)
        }
    }

    private fun convertJetton(
        from: WalletCurrency,
        value: Coins,
        to: WalletCurrency
    ): Coins {
        if (from == to || value.isZero) {
            return value
        }
        val fromRate = rateValue(from.address)
        val toRate = rateValue(to.address)
        if (fromRate.isZero || toRate.isZero) {
            return Coins.of(BigDecimal.ZERO, to.decimals)
        }
        val fiatValue = value.value.multiply(fromRate.value, Coins.mathContext)
        val finalAmount = fiatValue.divide(toRate.value, to.decimals, RoundingMode.HALF_EVEN)
        return Coins.of(finalAmount, to.decimals)
    }

    fun convert(token: String, value: Coins): Coins {
        if (currency.code == token || value == Coins.ZERO) {
            return value
        }

        val rate = rateValue(token)
        return (value * rate)
    }

    fun convertJetton(fromToken: String, toToken: String, value: Coins): Coins {
        if (fromToken == toToken || value == Coins.ZERO) {
            return value
        }
        val fromRate = rateValue(fromToken)
        val toRate = rateValue(toToken)

        if (fromRate.isZero || toRate.isZero) {
            return Coins.ZERO
        }
        val valueInMaster = value * fromRate
        return valueInMaster.div(toRate, roundingMode = RoundingMode.HALF_DOWN)
    }

    fun convertFromFiat(token: String, value: Coins): Coins {
        if (currency.code == token) {
            return value
        }

        val rate = rateValue(token)
        return value.div(rate, roundingMode = RoundingMode.HALF_EVEN)
    }

    fun getRate(token: String): Coins {
        return rateValue(token)
    }

    fun getDiff24h(token: String): String {
        return rateDiff(token)?.diff24h ?: ""
    }

    fun getDiff7d(token: String): String {
        return rateDiff(token)?.diff7d ?: ""
    }

    fun getDiff30d(token: String): String {
        return rateDiff(token)?.diff30d ?: ""
    }
}