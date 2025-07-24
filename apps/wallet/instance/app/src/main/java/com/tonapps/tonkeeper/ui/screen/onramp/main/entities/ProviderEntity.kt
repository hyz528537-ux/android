package com.tonapps.tonkeeper.ui.screen.onramp.main.entities

import android.os.Parcelable
import com.tonapps.wallet.api.entity.OnRampMerchantEntity
import com.tonapps.wallet.data.purchase.entity.PurchaseMethodEntity
import kotlinx.parcelize.Parcelize

@Parcelize
data class ProviderEntity(
    val id: String,
    val receive: Double,
    val title: String,
    val iconUrl: String,
    val widgetUrl: String,
    val buttons: List<PurchaseMethodEntity.Button>,
    val description: String? = null
): Parcelable {

    constructor(
        widget: OnRampMerchantEntity,
        details: PurchaseMethodEntity
    ) : this(
        id = details.id,
        receive = widget.amount,
        title = details.title,
        iconUrl = details.iconUrl,
        widgetUrl = widget.widgetUrl,
        buttons = details.infoButtons,
        description = details.description
    )
}