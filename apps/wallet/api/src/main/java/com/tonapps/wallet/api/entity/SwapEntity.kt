package com.tonapps.wallet.api.entity

import kotlinx.serialization.Serializable

object SwapEntity {

    @Serializable
    data class Args(
        val fromAsset: String,
        val toAsset: String,
        val fromAmount: String,
        val userAddress: String,
        val slippage: Int
    )

    @Serializable
    data class Message(
        val targetAddress: String,
        val sendAmount: String,
        val payload: String?,
    )

    @Serializable
    data class Messages(
        val messages: List<Message>,
        val quoteId: String,
        val resolverName: String,
        val askUnits: String,
        val gasBudget: String,
        val estimatedGasConsumption: String
    )
}