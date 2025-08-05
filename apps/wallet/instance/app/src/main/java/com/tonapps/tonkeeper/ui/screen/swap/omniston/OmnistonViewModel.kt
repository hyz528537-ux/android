package com.tonapps.tonkeeper.ui.screen.swap.omniston

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.cellFromHex
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.mapList
import com.tonapps.extensions.single
import com.tonapps.extensions.singleValue
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.ledger.ton.Transaction
import com.tonapps.tonkeeper.core.InsufficientFundsException
import com.tonapps.tonkeeper.extensions.getTransfers
import com.tonapps.tonkeeper.extensions.getWalletTransfer
import com.tonapps.tonkeeper.extensions.method
import com.tonapps.tonkeeper.helper.BatteryHelper
import com.tonapps.tonkeeper.helper.TwinInput
import com.tonapps.tonkeeper.helper.TwinInput.Companion.opposite
import com.tonapps.tonkeeper.manager.assets.AssetsManager
import com.tonapps.tonkeeper.manager.tx.TransactionManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.send.main.helper.InsufficientBalanceType
import com.tonapps.tonkeeper.ui.screen.send.main.state.SendFee
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionScreen
import com.tonapps.tonkeeper.ui.screen.swap.omniston.state.OmnistonStep
import com.tonapps.tonkeeper.ui.screen.swap.omniston.state.SwapInputsState
import com.tonapps.tonkeeper.ui.screen.swap.omniston.state.SwapQuoteState
import com.tonapps.tonkeeper.ui.screen.swap.omniston.state.SwapTokenState
import com.tonapps.tonkeeper.ui.screen.swap.picker.SwapPickerScreen
import com.tonapps.tonkeeper.usecase.emulation.Emulated
import com.tonapps.tonkeeper.usecase.emulation.Emulated.Companion.buildFee
import com.tonapps.tonkeeper.usecase.emulation.EmulationUseCase
import com.tonapps.tonkeeper.usecase.sign.SignUseCase
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.SendBlockchainState
import com.tonapps.wallet.api.entity.SwapEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.MessageBodyEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.battery.BatteryMapper
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.data.core.entity.RawMessageEntity
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.core.query
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.BatteryTransaction
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.PreferredFeeMethod
import com.tonapps.wallet.data.swap.SwapRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer
import uikit.extensions.collectFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

class OmnistonViewModel(
    app: Application,
    args: OmnistonArgs,
    private val wallet: WalletEntity,
    private val swapRepository: SwapRepository,
    private val tokenRepository: TokenRepository,
    private val assetsManager: AssetsManager,
    private val api: API,
    private val signUseCase: SignUseCase,
    private val transactionManager: TransactionManager,
    private val accountRepository: AccountRepository,
    private val batteryRepository: BatteryRepository,
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
    private val emulationUseCase: EmulationUseCase,
): BaseWalletVM(app) {

    private companion object {
        private val defaultFromCurrency = WalletCurrency.TON
        private val defaultToCurrency = WalletCurrency.USDT_TON
    }

    val installId: String
        get() = settingsRepository.installId

    val swapUri: Uri
        get() = api.config.swapUri

    private val twinInput = TwinInput(viewModelScope)

    private val _requestFocusFlow = MutableEffectFlow<TwinInput.Type?>()
    val requestFocusFlow = _requestFocusFlow.asSharedFlow().filterNotNull()

    private val lastSeqNo = AtomicInteger(0)
    private val walletsCountRef = AtomicInteger(0)

    private val _amountFlow = MutableEffectFlow<Coins>()
    @OptIn(FlowPreview::class)
    private val amountFlow = _amountFlow.asSharedFlow().debounce(1000)

    private val _quoteStateFlow = MutableStateFlow(SwapQuoteState())
    @OptIn(FlowPreview::class)
    val quoteStateFlow = _quoteStateFlow.asStateFlow().debounce(100)

    private val _stepFlow = MutableStateFlow(OmnistonStep.Input)
    val stepFlow = _stepFlow.asStateFlow()

    private val ratesFlow = swapRepository.assetsFlow
        .mapList { it.address }
        .map { ratesRepository.getRates(settingsRepository.currency, it) }

    val sendOutputValueFlow = twinInput.createConvertFlow(ratesFlow, TwinInput.Type.Send)
    val receiveOutputValueFlow = twinInput.createConvertFlow(ratesFlow, TwinInput.Type.Receive)

    val sendOutputCurrencyFlow = twinInput.stateFlow.map { it.sendCurrency }.distinctUntilChanged()
    val receiveOutputCurrencyFlow = twinInput.stateFlow.map { it.receiveCurrency }.distinctUntilChanged()

    val inputPrefixFlow = twinInput.stateFlow.map { it.focus.opposite }.distinctUntilChanged()

    val priceFlow = combine(ratesFlow, twinInput.stateFlow) { rates, inputsState ->
        val coins = Coins.ONE
        val value = inputsState.convert(
            rates = rates,
            value = coins
        )
        val formatFrom = CurrencyFormatter.format(inputsState.sendCurrency.code, coins)
        val formatTo = CurrencyFormatter.format(inputsState.receiveCurrency.code, value)
        "$formatFrom ≈ $formatTo"
    }

    private val tokenBalanceFlow = twinInput.stateFlow
        .map { it.sendCurrency }
        .distinctUntilChanged()
        .map { send ->
            assetsManager.getToken(wallet, send.address)
        }

    val uiStateToken = combine(
        tokenBalanceFlow,
        twinInput.stateFlow.map { it.send.coins }.distinctUntilChanged()
    ) { token, sendAmount ->
        if (token == null) {
            SwapTokenState()
        } else {
            val remaining = token.token.balance.value - sendAmount
            SwapTokenState(
                fromToken = token,
                remaining = remaining
            )
        }
    }

    val uiButtonEnabledFlow = combine(
        uiStateToken.map { it.insufficientBalance }.distinctUntilChanged(),
        twinInput.stateFlow.map { it.isEmpty }.distinctUntilChanged()
    ) { insufficientBalance, isEmpty ->
        !insufficientBalance && !isEmpty
    }

    val jettonSymbolFrom: String
        get() = twinInput.state.getCurrency(TwinInput.Type.Send).symbol + "_ton"

    val jettonSymbolTo: String
        get() = twinInput.state.getCurrency(TwinInput.Type.Receive).symbol + "_ton"

    val providerName: String
        get() = _quoteStateFlow.value.provider.ifEmpty { "unknown" }

    val providerUrl: String
        get() = "unknown"

    private var pollingJob: Job? = null
    private var setSendCurrencyJob: Job? = null

    init {
        applyDefaultCurrencies()
        collectFlow(swapRepository.assetsFlow.take(1)) { applyArgs(args, it) }
    }

    fun updateFocusInput(type: TwinInput.Type) {
        twinInput.updateFocus(type)
    }

    fun updateSendCurrency(currency: WalletCurrency) {
        twinInput.updateCurrency(TwinInput.Type.Send, currency)
    }

    fun updateSendInput(amount: String) {
        twinInput.updateValue(TwinInput.Type.Send, amount)
    }

    fun updateReceiveInput(amount: String) {
        twinInput.updateValue(TwinInput.Type.Receive, amount)
    }

    fun updateReceiveCurrency(currency: WalletCurrency) {
        twinInput.updateCurrency(TwinInput.Type.Receive, currency)
    }

    private fun applyDefaultCurrencies() {
        updateSendCurrency(defaultFromCurrency)
        updateReceiveCurrency(defaultToCurrency)
    }

    private fun applyArgs(
        args: OmnistonArgs,
        availableCurrencies: List<WalletCurrency>
    ) {
        val from = availableCurrencies.query(args.fromToken) ?: defaultFromCurrency
        val to = availableCurrencies.query(args.toToken) ?: defaultToCurrency
        if (from == to) {
            applyDefaultCurrencies()
        } else {
            updateSendCurrency(from)
            updateReceiveCurrency(to)
        }
    }

    fun switch() {
        twinInput.switch()
    }

    fun pickCurrency(forType: TwinInput.Type) = viewModelScope.launch {
        runCatching {
            val selectedCurrency = twinInput.getCurrency(forType)
            val ignoreCurrency = twinInput.getCurrency(forType.opposite)
            SwapPickerScreen.run(context, wallet, selectedCurrency, ignoreCurrency, forType == TwinInput.Type.Send)
        }.onSuccess { currency ->
            if (currency == twinInput.state.getCurrency(forType)) {
                return@onSuccess
            } else if (twinInput.state.hasCurrency(currency)) {
                _requestFocusFlow.tryEmit(forType)
                switch()
            } else {
                _requestFocusFlow.tryEmit(twinInput.state.focus)
                twinInput.updateCurrency(forType, currency)
            }
        }
    }

    private suspend fun requestTONToken(): AccountTokenEntity? {
        return tokenRepository.getTON(
            currency = settingsRepository.currency,
            accountId = wallet.accountId,
            testnet = wallet.testnet,
        )
    }

    private suspend fun isSingleWallet(): Boolean {
        if (walletsCountRef.get() == 0) {
            walletsCountRef.set(accountRepository.getWallets().size)
        }
        return walletsCountRef.get() == 1
    }

    suspend fun next() = withContext(Dispatchers.IO) {
        val tonBalance = requestTONToken() ?: throw Exception("TON token not found")
        val token = tokenBalanceFlow.singleValue()?.token ?: throw Exception("Token not found")
        val inputState = twinInput.state
        var fromAmount = inputState.send.coins
        if (token.isTon) {
            val requiredForFee = api.config.meanFeeSwap + Coins.of("0.25")
            if (requiredForFee > token.balance.value) {
                throw InsufficientFundsException(
                    currency = WalletCurrency.TON,
                    required = requiredForFee,
                    available = token.balance.value,
                    type = InsufficientBalanceType.InsufficientBalanceForFee,
                    withRechargeBattery = false,
                    singleWallet = isSingleWallet()
                )
            }

            val diff = token.balance.value - fromAmount
            if (requiredForFee >= diff) {
                fromAmount -= requiredForFee
            }
            if (fromAmount.isNegative) {
                throw InsufficientFundsException(
                    currency = WalletCurrency.TON,
                    required = requiredForFee,
                    available = token.balance.value,
                    type = InsufficientBalanceType.InsufficientBalanceWithFee,
                    withRechargeBattery = false,
                    singleWallet = isSingleWallet()
                )
            }
        }

        val args = SwapEntity.Args(
            fromAsset = inputState.send.address.toRawAddress(),
            toAsset = inputState.receive.address.toRawAddress(),
            fromAmount = fromAmount.toNano(),
            userAddress = wallet.address.toRawAddress(),
            slippage = 1
        )
        withContext(Dispatchers.Main) {
            startPolling(
                args = args,
                fromCurrency = inputState.send.currency,
                toCurrency = inputState.receive.currency,
                tonBalance = tonBalance
            )
        }
    }

    private fun getLedgerTransaction(
        message: MessageBodyEntity
    ): List<Transaction> {
        if (!message.wallet.isLedger) {
            return emptyList()
        }
        val transactions = mutableListOf<Transaction>()
        for ((index, transfer) in message.transfers.withIndex()) {
            transactions.add(
                Transaction.fromWalletTransfer(
                    walletTransfer = transfer,
                    seqno = message.seqNo + index,
                    timeout = message.validUntil
                )
            )
        }

        return transactions.toList()
    }

    fun setFeeMethod(fee: SendFee) {
        settingsRepository.setPreferredFeeMethod(wallet.id, fee.method)
        _quoteStateFlow.update { state ->
            state.copy(selectedFee = fee)
        }
    }

    fun sign(callback: (isSuccessful: Boolean) -> Unit) {
        stopPolling()
        val state = _quoteStateFlow.value
        val signRequest = state.signRequest ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isBattery = state.isPreferredFeeMethodBattery
                val transfers = transfers(signRequest,false, isBattery)
                val validUntil = accountRepository.getValidUntil(wallet.testnet)
                val message = accountRepository.messageBody(wallet, validUntil, transfers)
                val unsignedBody = message.createUnsignedBody(isBattery)
                val ledgerTransactions = getLedgerTransaction(message)

                val cells = mutableListOf<Cell>()
                if (ledgerTransactions.size > 1) {
                    for ((index, transaction) in ledgerTransactions.withIndex()) {
                        val cell = signUseCase(
                            context = context,
                            wallet = wallet,
                            seqNo = transaction.seqno,
                            ledgerTransaction = transaction,
                            transactionIndex = index,
                            transactionCount = ledgerTransactions.size
                        )
                        cells.add(cell)
                    }
                } else {
                    val cell = signUseCase(
                        context = context,
                        wallet = wallet,
                        unsignedBody = unsignedBody,
                        ledgerTransaction = ledgerTransactions.firstOrNull(),
                        seqNo = getSeqNo()
                    )
                    cells.add(cell)
                }
                val confirmationTimeMillis = state.timestamp - System.currentTimeMillis()
                val states = mutableListOf<SendBlockchainState>()
                for (cell in cells) {
                    val boc = cell.base64()
                    val status = transactionManager.send(
                        wallet = wallet,
                        boc = boc,
                        withBattery = isBattery,
                        source = "swap",
                        confirmationTime = confirmationTimeMillis / 1000.0
                    )
                    states.add(status)
                }

                val isSuccessful = states.all { it == SendBlockchainState.SUCCESS }
                withContext(Dispatchers.Main) {
                    callback(isSuccessful)
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    callback(false)
                    next()
                }
            }
        }
    }

    private fun createMessages(messages: List<SwapEntity.Message>): SignRequestEntity? {
        if (messages.isEmpty()) {
            return null
        }
        val builder = SignRequestEntity.Builder().setTestnet(wallet.testnet)
        messages.forEach { omnistionTonMessage ->
            val payload = omnistionTonMessage.payload?.cellFromHex()
            builder.addMessage(RawMessageEntity.of(
                address = omnistionTonMessage.targetAddress,
                amount = Coins.ofNano(omnistionTonMessage.sendAmount).toLong(),
                payload = payload?.base64()
            ))
        }
        return builder.build(Uri.EMPTY)
    }

    private suspend fun getSeqNo(): Int {
        var seqNo = lastSeqNo.get()
        if (seqNo == 0) {
            seqNo = accountRepository.getSeqno(wallet)
            lastSeqNo.set(seqNo)
        }
        return seqNo
    }

    private suspend fun isBatteryIsEnabledTx(): Boolean {
        if (twinInput.state.sendCurrency == WalletCurrency.TON) {
            return false
        }
        return BatteryHelper.isBatteryIsEnabledTx(wallet, BatteryTransaction.SWAP, settingsRepository, accountRepository, batteryRepository)
    }

    private suspend fun batteryEmulated(message: MessageBodyEntity) = BatteryHelper.emulation(
        wallet = wallet,
        message = message,
        emulationUseCase = emulationUseCase,
        accountRepository = accountRepository,
        batteryRepository = batteryRepository,
        api = api
    )

    private suspend fun getTonBalance() = tokenRepository.getTonBalance(settingsRepository.currency, wallet.accountId, wallet.testnet)

    private suspend fun transfers(
        request: SignRequestEntity,
        forEmulation: Boolean,
        batteryEnabled: Boolean
    ): List<WalletTransfer> {
        val excessesAddress = if (!forEmulation) {
            batteryRepository.getConfig(wallet.testnet).excessesAddress
        } else null

        return request.getTransfers(
            wallet = wallet,
            api = api,
            batteryEnabled = batteryEnabled,
            compressedTokens = emptyList(),
            excessesAddress = excessesAddress,
            tonBalance = getTonBalance()
        )
    }

    private suspend fun createEmulationTx(
        signRequest: SignRequestEntity,
        batteryEnabled: Boolean
    ): SwapQuoteState.Tx = withContext(Dispatchers.IO) {
        val validUntil = accountRepository.getValidUntil(wallet.testnet)
        val messageBody = MessageBodyEntity(
            wallet = wallet,
            seqNo = getSeqNo(),
            validUntil = validUntil,
            transfers = transfers(signRequest,true, batteryEnabled)
        )

        val tonDeferred = async {
            emulationUseCase(
                message = messageBody,
                useBattery = false,
                forceRelayer = false,
                params = true
            )
        }

        val batteryDeferred = async {
            if (batteryEnabled) {
                batteryEmulated(messageBody)
            } else {
                null
            }
        }

        val tonEmulated = tonDeferred.await()
        val batteryEmulated = batteryDeferred.await()

        val batteryFee = if (batteryEmulated != null && !batteryEmulated.failed) {
            batteryEmulated.buildFee(wallet, api, accountRepository, batteryRepository, ratesRepository)
        } else null

        val tonFee = tonEmulated.buildFee(wallet, api, accountRepository, batteryRepository, ratesRepository)

        SwapQuoteState.Tx(
            sendTonFee = tonFee,
            tonEmulated = tonEmulated,
            sendBatteryFee = batteryFee,
            batteryEmulated = batteryEmulated,
            messageBody = messageBody
        )
    }

    private suspend fun fetchMessages(
        args: SwapEntity.Args,
        fromCurrency: WalletCurrency,
        toCurrency: WalletCurrency,
        batteryEnabled: Boolean,
        tonBalance: AccountTokenEntity,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val messages = api.swapOmnistonBuild(args)
            val signRequest = createMessages(messages.messages) ?: return@withContext false
            val tx = createEmulationTx(signRequest, batteryEnabled)
            var preferredFeeMethod = settingsRepository.getPreferredFeeMethod(wallet.id)
            var canEditFeeMethod = true
            val gasBudget = Coins.ofNano(messages.gasBudget)
            val estimatedGasConsumption = Coins.ofNano(messages.estimatedGasConsumption)
            val totalTonFee = tx.tonEmulated?.totalFees ?: api.config.meanFeeSwap
            val maxRequiredFee = listOf(gasBudget, estimatedGasConsumption, totalTonFee).max()
            var insufficientFunds: InsufficientFundsException? = null
            if (fromCurrency == WalletCurrency.TON && maxRequiredFee > tonBalance.balance.value) {
                insufficientFunds = InsufficientFundsException(
                    currency = WalletCurrency.TON,
                    required = maxRequiredFee,
                    available = tonBalance.balance.value,
                    type = InsufficientBalanceType.InsufficientBalanceWithFee,
                    withRechargeBattery = false,
                    singleWallet = isSingleWallet()
                )
            } else if (fromCurrency != WalletCurrency.TON) {
                if (tx.batteryEmulated == null && maxRequiredFee > tonBalance.balance.value) {
                    insufficientFunds = InsufficientFundsException(
                        currency = WalletCurrency.TON,
                        required = maxRequiredFee,
                        available = tonBalance.balance.value,
                        type = InsufficientBalanceType.InsufficientBalanceForFee,
                        withRechargeBattery = true,
                        singleWallet = isSingleWallet()
                    )
                } else if (maxRequiredFee > tonBalance.balance.value) {
                    preferredFeeMethod = PreferredFeeMethod.BATTERY
                    canEditFeeMethod = false
                }
            }

            _quoteStateFlow.value = SwapQuoteState(
                toUnits = Coins.ofNano(messages.askUnits, toCurrency.decimals),
                provider = messages.resolverName,
                fromCurrency = fromCurrency,
                toCurrency = toCurrency,
                signRequest = signRequest,
                fromUnits = Coins.ofNano(args.fromAmount, fromCurrency.decimals),
                gasBudget = gasBudget,
                estimatedGasConsumption = estimatedGasConsumption,
                tx = tx,
                selectedFee = tx.getFeeByMethod(preferredFeeMethod),
                insufficientFunds = insufficientFunds,
                canEditFeeMethod = canEditFeeMethod,
                meanFeeSwap = api.config.meanFeeSwap
            )

            return@withContext true
        } catch (ignored: Throwable) {
            lastSeqNo.set(0)
        }
        return@withContext false
    }

    private fun startPolling(
        args: SwapEntity.Args,
        fromCurrency: WalletCurrency,
        toCurrency: WalletCurrency,
        tonBalance: AccountTokenEntity,
    ) {
        stopPolling()

        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            val batteryEnabled = isBatteryIsEnabledTx()
            while (isActive) {
                if (!fetchMessages(args, fromCurrency, toCurrency, batteryEnabled, tonBalance)) {
                    delay(1000)
                    continue
                }
                if (isActive && _stepFlow.value == OmnistonStep.Input) {
                    _stepFlow.value = OmnistonStep.Review
                }
                delay(5000)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null

        setSendCurrencyJob?.cancel()
        setSendCurrencyJob = null
    }

    fun reset() {
        stopPolling()
        _stepFlow.value = OmnistonStep.Input
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}