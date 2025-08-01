package com.tonapps.tonkeeper.ui.screen.swap.omniston

import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.lifecycleScope
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.extensions.addFeeItem
import com.tonapps.tonkeeper.extensions.finishDelay
import com.tonapps.tonkeeper.extensions.hideKeyboard
import com.tonapps.tonkeeper.extensions.id
import com.tonapps.tonkeeper.extensions.routeToHistoryTab
import com.tonapps.tonkeeper.helper.TwinInput
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.popup.ActionSheet
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.screen.onramp.main.view.CurrencyInputView
import com.tonapps.tonkeeper.ui.screen.onramp.main.view.ReviewInputView
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeper.ui.screen.swap.omniston.state.OmnistonStep
import com.tonapps.tonkeeper.ui.screen.swap.omniston.state.SwapInputsState
import com.tonapps.tonkeeper.ui.screen.swap.omniston.state.SwapQuoteState
import com.tonapps.tonkeeper.ui.screen.swap.omniston.state.SwapTokenState
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentBlueColor
import com.tonapps.uikit.color.textAccentColor
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.extensions.collectFlow
import uikit.extensions.doKeyboardAnimation
import uikit.extensions.dp
import uikit.extensions.reject
import uikit.extensions.rotate180Animation
import uikit.extensions.withBlueBadge
import uikit.extensions.withClickable
import uikit.extensions.withInterpunct
import uikit.span.ClickableSpanCompat
import uikit.widget.HeaderView
import uikit.widget.LoaderView
import uikit.widget.ModalHeader
import uikit.widget.ProcessTaskView
import uikit.widget.SlideActionView
import uikit.widget.SlideBetweenView
import uikit.widget.item.ItemLineView

class OmnistonScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_omniston, wallet), BaseFragment.BottomSheet {

    override val viewModel: OmnistonViewModel by walletViewModel {
        parametersOf(OmnistonArgs(requireArguments()))
    }

    private val feeMethodSelector: ActionSheet by lazy {
        ActionSheet(requireContext())
    }

    private val rootViewMode: RootViewModel by activityViewModel()

    private lateinit var slidesView: SlideBetweenView
    private lateinit var headerView: HeaderView
    private lateinit var modalHeaderView: ModalHeader
    private lateinit var sendInputView: CurrencyInputView
    private lateinit var receiveInputView: CurrencyInputView
    private lateinit var continueButton: Button
    private lateinit var actionContainerView: View
    private lateinit var detailsContainerView: View
    private lateinit var reviewSendView: ReviewInputView
    private lateinit var reviewReceiveView: ReviewInputView
    private lateinit var priceView: AppCompatTextView
    private lateinit var loaderView: LoaderView
    private lateinit var slideActionView: SlideActionView
    private lateinit var taskView: ProcessTaskView
    private lateinit var feeView: ItemLineView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analytics?.swapOpen(viewModel.swapUri, true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.edit).setOnClickListener { viewModel.reset() }

        headerView = view.findViewById(R.id.header)
        headerView.doOnCloseClick = { onBackPressed() }
        headerView.doOnActionClick = { finish() }

        taskView = view.findViewById(R.id.task)
        priceView = view.findViewById(R.id.price)
        loaderView = view.findViewById(R.id.loader)
        slideActionView = view.findViewById(R.id.slide_action)
        slideActionView.doOnDone = { sign() }
        feeView = view.findViewById(R.id.details_fee)

        val bottomReviewView = view.findViewById<View>(R.id.bottom_review)
        slidesView = view.findViewById(R.id.slides)

        modalHeaderView = view.findViewById(R.id.modal_header)
        modalHeaderView.onCloseClick = { finish() }

        reviewSendView = view.findViewById(R.id.review_send)
        reviewSendView.setTitleTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        reviewSendView.setValueTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        applyReviewSend()

        reviewReceiveView = view.findViewById(R.id.review_receive)
        reviewReceiveView.setBackgroundResource(uikit.R.drawable.bg_content_top)
        reviewReceiveView.setTitleTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        reviewReceiveView.setValueTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)

        sendInputView = view.findViewById(R.id.send_input)
        sendInputView.doOnTextChange = viewModel::updateSendInput
        sendInputView.doOnFocusChange = { hasFocus ->
            if (hasFocus) {
                viewModel.updateFocusInput(TwinInput.Type.Send)
            }
        }

        sendInputView.doOnCurrencyClick = {
            hideKeyboard()
            viewModel.pickCurrency(TwinInput.Type.Send)
        }

        receiveInputView = view.findViewById(R.id.receive_input)
        receiveInputView.doOnTextChange = viewModel::updateReceiveInput
        receiveInputView.doOnFocusChange = { hasFocus ->
            if (hasFocus) {
                viewModel.updateFocusInput(TwinInput.Type.Receive)
            }
        }
        receiveInputView.doOnCurrencyClick = {
            hideKeyboard()
            viewModel.pickCurrency(TwinInput.Type.Receive)
        }

        view.findViewById<View>(R.id.switch_button).setOnClickListener(::switch)

        reviewReceiveView.setOnClickListener {
            receiveInputView.focusWithKeyboard()
            viewModel.reset()
        }
        reviewSendView.setOnClickListener {
            sendInputView.focusWithKeyboard()
            viewModel.reset()
        }

        continueButton = view.findViewById(R.id.continue_button)
        continueButton.setOnClickListener { next() }

        actionContainerView = view.findViewById(R.id.action_container)

        detailsContainerView = view.findViewById(R.id.details_container)

        view.doKeyboardAnimation { offset, _, _ ->
            bottomReviewView.translationY = -offset.toFloat()
            actionContainerView.translationY = -offset.toFloat()
        }

        sendInputView.doOnEditorAction = { actionId ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                if (sendInputView.isEmpty) {
                    receiveInputView.focusWithKeyboard()
                } else {
                    next()
                }
                true
            } else {
                false
            }
        }

        receiveInputView.doOnEditorAction = { actionId ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                if (receiveInputView.isEmpty) {
                    sendInputView.focusWithKeyboard()
                } else {
                    next()
                }
                true
            } else {
                false
            }
        }

        collectFlow(viewModel.quoteStateFlow, ::applyQuoteState)
        collectFlow(viewModel.priceFlow, priceView::setText)
        collectFlow(viewModel.stepFlow) { step ->
            if (step == OmnistonStep.Input) {
                headerView.visibility = View.GONE
                modalHeaderView.visibility = View.VISIBLE
                slidesView.prev()
            } else {
                headerView.visibility = View.VISIBLE
                modalHeaderView.visibility = View.GONE
                slidesView.next()
                loaderView.visibility = View.GONE
                continueButton.visibility = View.VISIBLE
            }
        }

        collectFlow(viewModel.sendOutputCurrencyFlow, sendInputView::setCurrency)
        collectFlow(viewModel.sendOutputValueFlow, sendInputView::setValue)

        collectFlow(viewModel.receiveOutputCurrencyFlow, receiveInputView::setCurrency)
        collectFlow(viewModel.receiveOutputValueFlow, receiveInputView::setValue)
        collectFlow(viewModel.uiStateToken, ::applyTokenState)
        collectFlow(viewModel.uiButtonEnabledFlow, continueButton::setEnabled)
        collectFlow(viewModel.inputPrefixFlow, ::applyPrefix)

        collectFlow(viewModel.requestFocusFlow) { inputType ->
            when (inputType) {
                TwinInput.Type.Send -> sendInputView.focusWithKeyboard()
                TwinInput.Type.Receive -> receiveInputView.focusWithKeyboard()
            }
        }
    }

    override fun finish() {
        super.finish()
        hideKeyboard()
    }

    override fun onDragging() {
        super.onDragging()
        hideKeyboard()
    }

    private fun applyPrefix(inputType: TwinInput.Type) {
        if (inputType == TwinInput.Type.Send) {
            sendInputView.setPrefix(CurrencyInputView.EQUALS_SIGN_PREFIX)
            receiveInputView.setPrefix(null)
        } else {
            sendInputView.setPrefix(null)
            receiveInputView.setPrefix(CurrencyInputView.EQUALS_SIGN_PREFIX)
        }
    }

    private fun applyTokenState(state: SwapTokenState) {
        if (state.insufficientBalance) {
            sendInputView.setInsufficientBalance()
        } else {
            sendInputView.setTokenBalance(state.tokenBalance, state.remainingFormat, false)
        }
    }

    private fun applyReviewSend() {
        val prefix = getString(Localization.send)
        val edit = getString(Localization.edit)
        val text = "$prefix · $edit"
        val spannable = SpannableString(text)
        spannable.setSpan(ClickableSpanCompat(requireContext().textAccentColor) {
            viewModel.reset()
        }, prefix.length + 3, text.length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)

        reviewSendView.setTitleMovementMethod(LinkMovementMethod.getInstance())
        reviewSendView.setTitle(spannable)
    }

    private fun inputErrorState() {
        sendInputView.focusWithKeyboard()
        sendInputView.reject()
    }

    private fun signDefaultState() {
        slideActionView.reset()
        slideActionView.visibility = View.VISIBLE
        taskView.visibility = View.GONE
    }

    private fun signLoading() {
        taskView.visibility = View.VISIBLE
        taskView.state = ProcessTaskView.State.LOADING
        slideActionView.visibility = View.GONE
    }

    private fun signSuccess() {
        analytics?.swapSuccess(
            jettonSymbolFrom = viewModel.jettonSymbolFrom,
            jettonSymbolTo = viewModel.jettonSymbolTo,
            providerName = viewModel.providerName,
            providerUrl = viewModel.providerUrl,
            native = true,
        )

        rootViewMode.routeToHistoryTab("swap")
        taskView.visibility = View.VISIBLE
        taskView.state = ProcessTaskView.State.SUCCESS
        finishDelay()
    }

    private fun singFailure() {
        taskView.visibility = View.VISIBLE
        taskView.state = ProcessTaskView.State.FAILED
        postDelayed(5000) { signDefaultState() }
    }

    private fun sign() {
        signLoading()
        analytics?.swapConfirm(
            jettonSymbolFrom = viewModel.jettonSymbolFrom,
            jettonSymbolTo = viewModel.jettonSymbolTo,
            providerName = viewModel.providerName,
            providerUrl = viewModel.providerUrl,
            native = true,
        )
        viewModel.sign { isSuccessful ->
            if (isSuccessful) {
                signSuccess()
            } else {
                singFailure()
                slideActionView.reset()
            }
        }
    }

    private fun switch(view: View) {
        view.rotate180Animation()
        viewModel.switch()
    }

    override fun onBackPressed(): Boolean {
        if (slidesView.isFirst) {
            return super.onBackPressed()
        } else {
            viewModel.reset()
            return false
        }
    }

    private fun next() {
        hideKeyboard()
        loaderView.visibility = View.VISIBLE
        continueButton.visibility = View.GONE
        lifecycleScope.launch {
            try {
                viewModel.next()
            } catch (ignored: Throwable) {
                inputErrorState()
                loaderView.visibility = View.GONE
                continueButton.visibility = View.VISIBLE
            }
        }
        analytics?.swapClick(
            jettonSymbolFrom = viewModel.jettonSymbolFrom,
            jettonSymbolTo = viewModel.jettonSymbolTo,
            native = true,
            providerName = viewModel.providerName,
        )
    }

    private fun applyQuoteState(state: SwapQuoteState) {
        reviewSendView.setValue(state.fromUnitsFormat)
        receiveInputView.setValue(state.toUnits)
        reviewReceiveView.setValue(state.toUnitsFormat)
        applyDetailsContainer(state)
    }

    private fun applyDetailsContainer(state: SwapQuoteState) {
        setLineValue(R.id.details_provider, state.provider)
        setLineValue(R.id.details_rate, state.exchangeRate)
        applyFee(state)
    }

    private fun applyFee(state: SwapQuoteState) {
        setLineValue(R.id.details_fee, state.getFeeFormat(requireContext()))
        if (state.canUseBattery) {
            feeView.name = getString(Localization.fee).withInterpunct().withClickable(requireContext(), Localization.edit)
            feeView.setOnClickListener { selectFeeMethod(state) }
        } else {
            feeView.name = getString(Localization.fee)
            feeView.setOnClickListener(null)
        }
    }

    private fun selectFeeMethod(state: SwapQuoteState) {
        feeMethodSelector.width = 264.dp
        if (feeMethodSelector.isShowing) {
            return
        }

        feeMethodSelector.clearItems()
        state.feeOptions.forEach { fee ->
            feeMethodSelector.addFeeItem(fee, fee.id == state.selectedFee?.id) {
                viewModel.setFeeMethod(fee)
            }
        }
        feeMethodSelector.showPopupAboveRight(feeView)
    }

    private fun setLineValue(id: Int, value: CharSequence) {
        detailsContainerView.findViewById<ItemLineView>(id).value = value
    }

    override fun onResume() {
        super.onResume()
        sendInputView.focusWithKeyboard()
    }

    companion object {

        fun newInstance(
            wallet: WalletEntity,
            fromToken: String = "TON",
            toToken: String = TokenEntity.TON_USDT
        ): OmnistonScreen {
            val screen = OmnistonScreen(wallet)
            screen.setArgs(OmnistonArgs(fromToken, toToken))
            return screen
        }
    }
}