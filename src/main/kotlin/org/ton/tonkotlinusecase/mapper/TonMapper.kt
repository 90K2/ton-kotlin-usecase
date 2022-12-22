package org.ton.tonkotlinusecase.mapper

import org.springframework.stereotype.Component
import org.ton.block.*
import org.ton.cell.Cell
import org.ton.crypto.hex
import org.ton.tlb.loadTlb
import org.ton.tonkotlinusecase.constants.OpCodes
import org.ton.tonkotlinusecase.dto.*
import org.ton.tonkotlinusecase.getState
import org.ton.tonkotlinusecase.loadRemainingBits
import org.ton.tonkotlinusecase.toAddrString

@Component
class TonMapper {

    fun toAccountDTO(s: AccountInfo?): AccountResponseDTO? {
        if (s == null) return null
        val storageInit = s.storage.state.getState()
        return AccountResponseDTO(
            address = (s.addr as AddrStd).toAddrString(),
            status = s.storage.state.toString(),
            balance = s.storage.balance.coins.amount.toLong(),
            code = storageInit?.code?.value.toString(),
            data = storageInit?.data?.value.toString()
        )
    }

    private fun readComputePhase(t: TransOrd?): TrPhaseComputeVm? {
        if (t == null) return null
        return when (t.compute_ph) {
            is TrPhaseComputeVm -> t.compute_ph as TrPhaseComputeVm
            else -> null
        }
    }

    private fun readTxDescr(d: TransactionDescr): TransOrd? {
        return when (d) {
            is TransOrd -> d
            else -> null
        }
    }

    private fun mapMsg(
        info: CommonMsgInfo?,
        body: Either<Cell, Cell>?,
        init: Maybe<Either<StateInit, StateInit>>?,
        msgType: TonMsgType
    ): TonTxMsg? {
        if (info == null) return null
        val bodyValue = body?.toPair()?.toList()?.filter { it != null && !it.isEmpty() }?.firstOrNull()

        var msgAction = when {
            (msgType.inMsg() && body?.x != null && body.x?.isEmpty() == false) -> TonMsgAction.INVOCATION
            ( init?.value == null && bodyValue == null) -> TonMsgAction.TRANSFER
            (msgType.inMsg() && init != null) -> TonMsgAction.INIT
            else -> null
        }
        var comment: String? = null
        var opCode: Long? = null
        var ftAmount: Long? = null

        bodyValue?.parse { // transfer
            if (this.bits.size >= 32) {
                val tag = loadUInt(32).toLong()
                if (tag == 0xd53276db || tag == 0L) {
                    msgAction = TonMsgAction.TRANSFER
                    comment = String(loadRemainingBits().toByteArray())
                }
                if (tag == OpCodes.OP_JETTON_MINTER_MINT.toLong())
                    msgAction = TonMsgAction.INVOCATION
                if (tag == OpCodes.OP_WALLET_INTERTRANSFER.toLong()) {
                    loadUInt(64) // skip queryId
                    ftAmount = loadTlb(Coins.tlbCodec()).amount.toLong()
                }
                opCode = tag
            }
            loadRemainingBits()
        }

        if (info is IntMsgInfo)
            return TonTxMsg(
                value = info.value.coins.amount.toLong(),
                fwdFee = info.fwd_fee.amount.toLong(),
                ihrFee = info.ihr_fee.amount.toLong(),
                source = info.src.toAddrString(),
                destination = info.dest.toAddrString(),
                createdLt = info.created_lt,
                op = opCode,
                init = init?.value != null,
                msgAction = msgAction,
                msgType = msgType,
                transferAmount = ftAmount,
                comment = comment
            )

        return null
    }

    private fun mapMsgIn(m: Maybe<Message<Cell>>) = mapMsg(
        m.value?.info, m.value?.body, m.value?.init, TonMsgType.IN
    )
    private fun mapMsgOut(m: Message<Cell>) = mapMsg(
        m.info, m.body, m.init, TonMsgType.OUT
    )


    fun mapTx(tx: Transaction, blockId: Int, wc: Int): TonTxDTO {
        val descr = readTxDescr(tx.description)
        val computePh = readComputePhase(descr)
        return TonTxDTO(
            lt = tx.lt.toLong(),
            blockId = blockId,
            account = tx.account_addr,
            accountAddr = AddrStd(wc, tx.account_addr).toAddrString(),
            hash = hex(tx.hash()),
            gasFee = tx.total_fees.coins.amount.toLong(),
            actionFwdFee = descr?.action?.value?.total_fwd_fees?.value?.amount?.toLong(),
            actionFee = descr?.action?.value?.total_action_fees?.value?.amount?.toLong(),
            storageFee = descr?.storage_ph?.value?.storage_fees_collected?.amount?.toLong(),
            computeFee = computePh?.gas_fees?.amount?.toLong(),
            workchain = wc,
//            txRaw = tx,
            init = tx.orig_status != AccountStatus.ACTIVE && tx.end_status == AccountStatus.ACTIVE,
            inMsg = mapMsgIn(tx.in_msg),
            outMsg = tx.out_msgs.toMap().entries.mapNotNull { mapMsgOut(it.value) },
            computeSucceed = computePh?.success,
            computeExitCode = computePh?.exit_code,
            actionSucceed = descr?.action?.value?.success,
            actionExitCode = descr?.action?.value?.result_code
        )
    }

}
