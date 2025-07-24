package com.tonapps.tonkeeper.ui.screen.onramp.main

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.extensions.getProvidersByCountry
import com.tonapps.tonkeeper.extensions.onRampDataFlow
import com.tonapps.tonkeeper.helper.TwinInput
import com.tonapps.tonkeeper.helper.TwinInput.Companion.opposite
import com.tonapps.tonkeeper.manager.assets.AssetsManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.onramp.main.entities.ProviderEntity
import com.tonapps.tonkeeper.ui.screen.onramp.main.state.OnRampPaymentMethodState
import com.tonapps.tonkeeper.ui.screen.onramp.main.state.UiState
import com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.OnRampPickerScreen
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.api.entity.OnRampArgsEntity
import com.tonapps.wallet.api.entity.OnRampMerchantEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.currency.CurrencyCountries
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.data.purchase.PurchaseRepository
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import uikit.UiButtonState
import uikit.extensions.collectFlow

class OnRampViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val settingsRepository: SettingsRepository,
    private val ratesRepository: RatesRepository,
    private val assetsManager: AssetsManager,
    private val purchaseRepository: PurchaseRepository,
    private val api: API,
    private val accountRepository: AccountRepository
): BaseWalletVM(app) {

    val installId: String
        get() = settingsRepository.installId

    private val settings = OnRampSettings(app)

    private val _requestFocusFlow = MutableEffectFlow<TwinInput.Type?>()
    val requestFocusFlow = _requestFocusFlow.asSharedFlow().filterNotNull()

    private var observeInputButtonEnabledJob: Job? = null
    private var requestAvailableProvidersJob: Job? = null

    private val _selectedProviderIdFlow = MutableStateFlow<String?>(null)
    private val selectedProviderIdFlow = _selectedProviderIdFlow.asStateFlow()

    private val _availableProvidersFlow = MutableStateFlow<List<OnRampMerchantEntity>>(emptyList())
    private val availableProvidersFlow = _availableProvidersFlow.asStateFlow().filterNotNull()

    private val _selectedPaymentMethodFlow = MutableStateFlow(settings.getPaymentMethod())
    private val selectedPaymentMethodFlow = _selectedPaymentMethodFlow.asStateFlow()

    private val _stepFlow = MutableStateFlow(UiState.Step.Input)
    val stepFlow = _stepFlow.asStateFlow()

    private val onRampDataFlow = purchaseRepository.onRampDataFlow(settingsRepository, api)

    private val paymentMerchantsFlow = settingsRepository.countryFlow.map(purchaseRepository::getPaymentMethods)

    private val paymentMethodsFlow = paymentMerchantsFlow.map { merchants ->
        merchants.map { it.methods }.flatten().distinctBy { it.type }.filter { it.type != "apple_pay" }
    }

    private val merchantsFlow = settingsRepository.countryFlow.map {
        purchaseRepository.getProvidersByCountry(wallet, settingsRepository, it)
    }

    val providersFlow = combine(
        availableProvidersFlow,
        merchantsFlow.map { list ->
            list.associateBy { it.id }
        }
    ) { availableProviders, merchants ->
        availableProviders.mapNotNull { widget ->
            merchants[widget.merchant]?.let { ProviderEntity(widget, it) }
        }
    }.flowOn(Dispatchers.IO)

    private val twinInput = TwinInput(viewModelScope)

    val inputPrefixFlow = twinInput.stateFlow.map { it.focus.opposite }.distinctUntilChanged()
    val sendOutputCurrencyFlow = twinInput.stateFlow.map { it.sendCurrency }.distinctUntilChanged()
    val receiveOutputCurrencyFlow = twinInput.stateFlow.map { it.receiveCurrency }.distinctUntilChanged()

    val allowedPairFlow = combine(
        twinInput.currenciesStateFlow,
        onRampDataFlow.map { it.data }.distinctUntilChanged()
    ) { (send, receive), data ->
        data.findValidPairs(send.symbol, receive.symbol).pair
    }.distinctUntilChanged()

    val ratesFlow = twinInput.currenciesStateFlow.map { inputCurrencies ->
        val fiatCurrency = inputCurrencies.fiat ?: settingsRepository.currency
        ratesRepository.getRates(fiatCurrency, inputCurrencies.cryptoTokens.map { it.address })
    }.distinctUntilChanged()

    val minAmountFlow = combine(
        twinInput.currenciesStateFlow.map { it.send }.distinctUntilChanged(),
        allowedPairFlow.map { it?.min }.distinctUntilChanged()
    ) { sendCurrency, minAmount ->
        val coins = minAmount?.let {
            Coins.of(it, sendCurrency.decimals)
        } ?: return@combine null
        val formatted = CurrencyFormatter.format(sendCurrency.symbol, coins, replaceSymbol = false)
        UiState.MinAmount(coins, formatted)
    }

    val rateFormattedFlow = combine(
        twinInput.currenciesStateFlow,
        ratesFlow
    ) { inputCurrencies, rates ->
        val value = Coins.ONE
        val firstLinePrefix = CurrencyFormatter.format(inputCurrencies.send.symbol, value, replaceSymbol = false)
        val firstRate = rates.convert(inputCurrencies.send, value, inputCurrencies.receive)
        val firstLineSuffix = CurrencyFormatter.format(inputCurrencies.receive.symbol, firstRate, replaceSymbol = false)
        val firstLine = "$firstLinePrefix ≈ $firstLineSuffix"

        val secondLinePrefix = CurrencyFormatter.format(inputCurrencies.receive.symbol, value, replaceSymbol = false)
        val secondRate = rates.convert(inputCurrencies.receive, value, inputCurrencies.send)
        val secondLineSuffix = CurrencyFormatter.format(inputCurrencies.send.symbol, secondRate, replaceSymbol = false)
        val secondLine = "$secondLinePrefix ≈ $secondLineSuffix"
        UiState.RateFormatted(firstLine, secondLine)
    }.distinctUntilChanged()

    val sendOutputValueFlow = twinInput.createConvertFlow(ratesFlow, TwinInput.Type.Send)
    val receiveOutputValueFlow = twinInput.createConvertFlow(ratesFlow, TwinInput.Type.Receive)

    val balanceUiStateFlow = twinInput.stateFlow.map { it.send }.distinctUntilChanged().map { sendInput ->
        val token = if (sendInput.currency.fiat) null else assetsManager.getToken(wallet, sendInput.currency.address)
        val balance = token?.token?.balance
        val remainingFormat = createRemainingFormat(balance, sendInput.coins)
        UiState.Balance(
            balance = token?.token?.balance,
            insufficientBalance = remainingFormat.second,
            remainingFormat = remainingFormat.first
        )
    }.flowOn(Dispatchers.IO)

    val selectedProviderUiStateFlow = combine(
        providersFlow,
        selectedProviderIdFlow
    ) { providers, selectedProviderId ->
        UiState.SelectedProvider(
            send = twinInput.state.sendCurrency,
            receive = twinInput.state.receiveCurrency,
            providers = providers,
            selectedProviderId = selectedProviderId,
            sendAmount = twinInput.state.send.coins
        )
    }

    private val _inputButtonUiStateFlow = MutableStateFlow<UiButtonState>(UiButtonState.Default(false))
    private val inputButtonUiStateFlow = _inputButtonUiStateFlow.asStateFlow()

    private val _confirmButtonUiStateFlow = MutableStateFlow<UiButtonState>(UiButtonState.Default(true))
    val confirmButtonUiStateFlow = _confirmButtonUiStateFlow.asStateFlow()

    val buttonUiStateFlow = combine(
        stepFlow,
        inputButtonUiStateFlow,
        confirmButtonUiStateFlow,
    ) { step, inputButton, confirmButton ->
        if (step == UiState.Step.Input) {
            inputButton
        } else {
            confirmButton
        }
    }.distinctUntilChanged()

    val paymentMethodUiStateFlow = combine(
        selectedPaymentMethodFlow,
        paymentMethodsFlow
    ) { selectedType, methods ->
        OnRampPaymentMethodState(
            selectedType = selectedType,
            methods = methods.map {
                OnRampPaymentMethodState.createMethod(context, it, settingsRepository.country)
            }.sortedBy { method ->
                val index = OnRampPaymentMethodState.sortKeys.indexOf(method.type)
                if (index == -1) Int.MAX_VALUE else index
            }
        )
    }

    val purchaseType: String
        get() = if (twinInput.state.sendCurrency.fiat) "buy" else "sell"

    val network: String
        get() = OnRampUtils.resolveNetwork(twinInput.state.sendCurrency)

    val from: String
        get() = OnRampUtils.fixSymbol(twinInput.state.sendCurrency.symbol)

    val to: String
        get() = OnRampUtils.fixSymbol(twinInput.state.receiveCurrency.symbol)

    val fromForAnalytics: String
        get() = if (twinInput.state.sendCurrency.fiat) {
            "fiat"
        } else {
            "crypto_$from"
        }

    val toForAnalytics: String
        get() = if (twinInput.state.receiveCurrency.fiat) {
            "fiat"
        } else {
            "crypto_$to"
        }

    val country: String
        get() = settingsRepository.country

    val paymentMethod: String
        get() = _selectedPaymentMethodFlow.value ?: "unknown"

    init {
        applyDefaultCurrencies()
        startObserveInputButtonEnabled()

        combine(
            _selectedPaymentMethodFlow.filter { it == null },
            paymentMethodsFlow.filter { it.isNotEmpty() }
        ) { _, methods ->
            setSelectedPaymentMethod(methods.first().type)
        }.launch()
    }

    fun updateFocusInput(type: TwinInput.Type) {
        twinInput.updateFocus(type)
    }

    fun updateSendCurrency(currency: WalletCurrency, saveSettings: Boolean = true) {
        twinInput.updateCurrency(TwinInput.Type.Send, currency)
        if (saveSettings) {
            settings.setFromCurrency(currency)
        }
    }

    fun updateSendInput(amount: String) {
        twinInput.updateValue(TwinInput.Type.Send, amount)
    }

    fun updateReceiveInput(amount: String) {
        twinInput.updateValue(TwinInput.Type.Receive, amount)
    }

    fun updateReceiveCurrency(currency: WalletCurrency) {
        twinInput.updateCurrency(TwinInput.Type.Receive, currency)
        settings.setToCurrency(currency)
    }

    fun switch() {
        twinInput.switch()
        settings.setToCurrency(twinInput.state.sendCurrency)
        settings.setFromCurrency(twinInput.state.receiveCurrency)
    }

    fun reset() {
        cancelRequestAvailableProviders()
        startObserveInputButtonEnabled()
        _stepFlow.value = UiState.Step.Input
    }

    private fun startObserveInputButtonEnabled() {
        cancelObserveInputButtonEnabled()
        observeInputButtonEnabledJob = combine(
            balanceUiStateFlow,
            allowedPairFlow,
            twinInput.stateFlow,
            minAmountFlow.map { it?.amount ?: Coins.ZERO }.distinctUntilChanged(),
        ) { balance, pair, inputs, minAmount ->
            if (inputs.isEmpty || pair == null) {
                UiButtonState.Default(false)
            } else if (minAmount > inputs.send.coins) {
                UiButtonState.Default(false)
            } else if (inputs.sendCurrency.isTONChain || inputs.sendCurrency.isTronChain) {
                UiButtonState.Default(balance.balance != null && balance.balance.value.isPositive && !balance.insufficientBalance)
            } else {
                UiButtonState.Default(true)
            }
        }.distinctUntilChanged().collectFlow {
            _inputButtonUiStateFlow.value = it
        }
    }

    private fun cancelObserveInputButtonEnabled() {
        observeInputButtonEnabledJob?.cancel()
        observeInputButtonEnabledJob = null
    }

    private fun currencyByCountry(): WalletCurrency {
        val code = CurrencyCountries.getCurrencyCode(settingsRepository.country)
        return WalletCurrency.ofOrDefault(code)
    }

    private fun applyDefaultCurrencies() {
        val fromCurrency = settings.getFromCurrency()
        updateSendCurrency(fromCurrency ?: currencyByCountry(), fromCurrency != null)
        updateReceiveCurrency(settings.getToCurrency() ?: WalletCurrency.TON)
    }

    fun pickCurrency(forType: TwinInput.Type) = viewModelScope.launch {
        runCatching {
            OnRampPickerScreen.run(
                context = context,
                wallet = wallet,
                currency = twinInput.getCurrency(forType),
                send = forType == TwinInput.Type.Send
            )
        }.onSuccess { currency ->
            _requestFocusFlow.emit(forType)
            if (currency == twinInput.state.getCurrency(forType)) {
                return@onSuccess
            } else if (twinInput.state.hasCurrency(currency)) {
                switch()
            } else if (forType == TwinInput.Type.Send) {
                updateSendCurrency(currency)
            } else if (forType == TwinInput.Type.Receive) {
                updateReceiveCurrency(currency)
            }
        }
    }

    private fun createRemainingFormat(balance: BalanceEntity?, value: Coins): Pair<CharSequence?, Boolean> {
        if (balance == null || value.isZero) {
            return Pair(null, false)
        }
        val remaining = balance.value - value
        if (remaining == balance.value) {
            return Pair(null, false)
        }
        val format = CurrencyFormatter.format(balance.token.symbol, remaining)
        return Pair(format, remaining.isNegative)
    }

    private suspend fun requestWebViewLinkArg(paymentMethod: String?): OnRampArgsEntity {
        var walletAddress = ""
        if (twinInput.state.hasTonChain) {
            walletAddress = wallet.address
        } else if (twinInput.state.hasTronChain) {
            walletAddress = accountRepository.getTronAddress(wallet.id) ?: ""
        }

        return OnRampArgsEntity(
            from = from,
            to = to,
            network = network,
            wallet = walletAddress,
            purchaseType = purchaseType,
            amount = twinInput.state.send.coins,
            country = settingsRepository.country,
            paymentMethod = paymentMethod
        )
    }

    private fun cancelRequestAvailableProviders() {
        requestAvailableProvidersJob?.cancel()
        requestAvailableProvidersJob = null
    }

    private fun setLoading(loading: Boolean) {
        if (_stepFlow.value == UiState.Step.Input && loading) {
            _inputButtonUiStateFlow.value = UiButtonState.Loading
        } else if (_stepFlow.value == UiState.Step.Confirm && loading) {
            _confirmButtonUiStateFlow.value = UiButtonState.Loading
        } else if (_stepFlow.value == UiState.Step.Confirm) {
            _confirmButtonUiStateFlow.value = UiButtonState.Default(true)
        }
    }

    fun requestAvailableProviders(selectedPaymentMethod: String? = _selectedPaymentMethodFlow.value) {
        if (twinInput.state.isEmpty) {
            return
        }

        cancelRequestAvailableProviders()
        cancelObserveInputButtonEnabled()
        setLoading(true)

        requestAvailableProvidersJob = viewModelScope.launch {
            try {
                val args = requestWebViewLinkArg(selectedPaymentMethod)
                val availableProviders = api.calculateOnRamp(args)
                if (availableProviders.isEmpty()) {
                    throw IOException("No providers available for the selected payment method")
                } else {
                    _availableProvidersFlow.value = availableProviders
                    _stepFlow.value = UiState.Step.Confirm
                    setLoading(false)
                }
            } catch (ignored: CancellationException) { } catch (e: Exception) {
                toast(Localization.unknown_error)
                reset()
            }
        }
    }

    fun setSelectedProviderId(id: String?) {
        _selectedProviderIdFlow.value = id
    }

    fun isPurchaseOpenConfirm(providerId: String) = settingsRepository.isPurchaseOpenConfirm(wallet.id, providerId)

    fun disableConfirmDialog(wallet: WalletEntity, providerId: String) {
        settingsRepository.disablePurchaseOpenConfirm(wallet.id, providerId)
    }

    fun setSelectedPaymentMethod(type: String) {
        _selectedPaymentMethodFlow.value = type
        requestAvailableProviders(type)
        settings.setPaymentMethod(type)
    }

    override fun onCleared() {
        super.onCleared()
        cancelRequestAvailableProviders()
        cancelObserveInputButtonEnabled()
    }
}