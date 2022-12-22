package org.ton.tonkotlinusecase.contracts.wallet

import org.ton.api.pk.PrivateKeyEd25519
import org.ton.block.*
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.storeRef
import org.ton.contract.wallet.v4.AbstractContractV4
import org.ton.lite.api.LiteApi
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.storeTlb

abstract class AbstractWallet(
    liteApi: LiteApi,
    privateKey: PrivateKeyEd25519,
) : AbstractContractV4(liteApi, privateKey) {

    fun createTransferMessageV2(
        dest: MsgAddressInt,
        bounce: Boolean,
        amount: Coins,
        seqno: Int,
        payload: Cell,
        sendMode: Int = 3,
        destinationStateInit: StateInit? = null,
    ): Message<Cell> {
        val stateInit = createStateInit()
        val address = address(stateInit)
        val info = ExtInMsgInfo(address)
        val signingMessage = createSigningMessage(seqno) {
            storeUInt(sendMode, 8)
            storeRef {
                val messageRelaxed = MessageRelaxed(
                    info = CommonMsgInfoRelaxed.IntMsgInfoRelaxed(
                        ihr_disabled = true,
                        bounce = bounce,
                        bounced = false,
                        src = AddrNone,
                        dest = dest,
                        value = CurrencyCollection(
                            coins = amount
                        )
                    ),
                    init = destinationStateInit,
                    body = payload,
                    storeBodyInRef = true
                )
                storeTlb(MessageRelaxed.tlbCodec(AnyTlbConstructor), messageRelaxed)
            }
        }
        val signature = privateKey.sign(signingMessage.hash())
        val body = CellBuilder.createCell {
            storeBytes(signature)
            storeBits(signingMessage.bits)
            storeRefs(signingMessage.refs)
        }
        return Message(
            info = info,
            init = null,
            body = body,
            storeInitInRef = false,
            storeBodyInRef = true
        )
    }
}
