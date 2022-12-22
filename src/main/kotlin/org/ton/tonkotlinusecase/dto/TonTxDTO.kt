package org.ton.tonkotlinusecase.dto

import org.ton.bitstring.BitString
import org.ton.block.Transaction

data class TonTxRawDTO(
    val tx: Transaction, val blockId: Int, val wc: Int
)

data class TonTxDTO(
    val blockId: Int,
    val hash: String,
    val accountAddr: String,
    val account: BitString,
    val lt: Long,
    val workchain: Int,
//    val txRaw: Transaction,
    val gasFee: Long,
    val storageFee: Long?,
    val computeFee: Long?,
    val actionFee: Long?,
    val actionFwdFee: Long?,
    val inMsg: TonTxMsg?,
    val outMsg: List<TonTxMsg>,
    val init: Boolean,
    val computeSucceed: Boolean?,
    val computeExitCode: Int?,
    val actionSucceed: Boolean?,
    val actionExitCode: Int?
)
