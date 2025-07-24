package com.tonapps.tonkeeper.ui.screen.swap.picker.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.facebook.imagepipeline.common.ResizeOptions
import com.tonapps.tonkeeper.ui.component.CountryFlagView
import com.tonapps.tonkeeper.ui.screen.swap.picker.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.UIKitColor
import com.tonapps.uikit.color.resolveColor
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import uikit.extensions.drawable
import uikit.widget.FrescoView

class TokenHolder(
    parent: ViewGroup,
    private val onClick: (Item.Token) -> Unit,
): Holder<Item.Token>(parent, R.layout.view_currency_item) {

    private val imageView = findViewById<FrescoView>(R.id.image)
    private val iconView = findViewById<CountryFlagView>(R.id.icon)
    private val symbolView = findViewById<AppCompatTextView>(R.id.symbol)
    private val nameView = findViewById<AppCompatTextView>(R.id.name)
    private val checkView = findViewById<View>(R.id.check)

    init {
        findViewById<View>(R.id.arrow).visibility = View.GONE
        imageView.visibility = View.VISIBLE
        iconView.visibility = View.GONE
    }

    override fun onBind(item: Item.Token) {
        itemView.setOnClickListener { onClick(item) }
        itemView.background = item.position.drawable(context)
        imageView.setPlaceholder(null)
        imageView.setImageURIWithResize(item.iconUri, ResizeOptions.forSquareSize(72)!!)

        symbolView.text = item.code
        nameView.text = item.name
        checkView.visibility = if (item.selected) View.VISIBLE else View.GONE
    }

}