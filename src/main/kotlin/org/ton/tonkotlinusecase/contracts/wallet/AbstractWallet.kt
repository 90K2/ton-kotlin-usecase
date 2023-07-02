package org.ton.tonkotlinusecase.contracts.wallet

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.bitstring.BitString
import org.ton.block.*
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.contract.wallet.WalletContract
import org.ton.contract.wallet.WalletTransfer
import org.ton.lite.api.LiteApi
import org.ton.lite.api.liteserver.LiteServerAccountId
import org.ton.lite.api.liteserver.functions.LiteServerGetMasterchainInfo
import org.ton.lite.api.liteserver.functions.LiteServerRunSmcMethod
import org.ton.lite.api.liteserver.functions.LiteServerSendMessage
import org.ton.lite.client.LiteClient
import org.ton.tl.ByteString.Companion.toByteString
import org.ton.tlb.CellRef
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.storeRef
import org.ton.tonkotlinusecase.contracts.RootContract
import org.ton.tonkotlinusecase.contracts.SmartContractAnswer

abstract class AbstractWallet(
    open val privateKey: PrivateKeyEd25519,
    open val workchain: Int = 0,
    open val subWalletId: Int = WalletContract.DEFAULT_WALLET_ID + workchain,
    liteClient: LiteClient
) : RootContract(liteClient) {

    override fun createDataInit(): Cell = CellBuilder.createCell {
        storeUInt(0, 32) // seqno
        storeUInt(subWalletId, 32)
        storeBytes(privateKey.publicKey().key.toByteArray())
        storeUInt(0, 1) // plugins dict empty
    }

    suspend fun transfer(vararg transfers: WalletTransfer) {
        transfer(privateKey, *transfers)
    }

    suspend fun transfer(
            privateKey: PrivateKeyEd25519,
            vararg transfers: WalletTransfer
    ): Unit = coroutineScope {
        val seqno = async { kotlin.runCatching { getSeqno(liteClient.liteApi) }.getOrNull() ?: 0 }
        transfer(privateKey, seqno.await(), *transfers)
    }

    suspend fun getSeqno(liteApi: LiteApi): Int {
        val stack = runGetMethod(liteApi, "seqno").let {
            check(it.success && it.stack != null) { "seqno failed with exit code ${it.exitCode}" }
            it.stack
        }
        return stack?.toMutableVmStack()?.popInt()?.toInt() ?: throw RuntimeException("get seqno error")
    }

    private suspend fun transfer(
            privateKey: PrivateKeyEd25519,
            seqno: Int,
            vararg transfers: WalletTransfer
    ) {
        val message = createTransferMessage(
                address = address as AddrStd,
                stateInit = if (seqno == 0) createStateInit() else null,
                privateKey = privateKey,
                validUntil = Int.MAX_VALUE,
                walletId = subWalletId,
                seqno = seqno,
                payload = transfers
        )
        liteClient.sendMessage(message)
//        sendExternalMessage(liteApi, buildCell {
//            storeTlb(Message.tlbCodec(AnyTlbConstructor), message)
//        })
    }

    companion object {

        fun createTransferMessage(
                address: AddrStd,
                stateInit: StateInit?,
                privateKey: PrivateKeyEd25519,
                walletId: Int,
                validUntil: Int,
                seqno: Int,
                vararg payload: WalletTransfer
        ): Message<Cell> {
            val info = ExtInMsgInfo(
                    src = AddrNone,
                    dest = address,
                    importFee = Coins()
            )
            val maybeStateInit =
                    Maybe.of(stateInit?.let { Either.of<StateInit, CellRef<StateInit>>(null, CellRef(it)) })
            val giftBody = createGiftMessageBody(
                    privateKey,
                    walletId,
                    validUntil,
                    seqno,
                    *payload
            )
            val body = Either.of<Cell, CellRef<Cell>>(null, CellRef(giftBody))
            return Message(
                    info = info,
                    init = maybeStateInit,
                    body = body
            )
        }

        private fun createGiftMessageBody(
                privateKey: PrivateKeyEd25519,
                walletId: Int,
                validUntil: Int,
                seqno: Int,
                vararg gifts: WalletTransfer
        ): Cell {
            val unsignedBody = CellBuilder.createCell {
                storeUInt(walletId, 32)
                storeUInt(validUntil, 32)
                storeUInt(seqno, 32)
                storeUInt(0, 8) // OP_TRANSFER
                for (gift in gifts) {
                    var sendMode = 3
                    if (gift.sendMode > -1) {
                        sendMode = gift.sendMode
                    }
                    val intMsg = CellRef(createIntMsg(gift))

                    storeUInt(sendMode, 8)
                    storeRef(MessageRelaxed.tlbCodec(AnyTlbConstructor), intMsg)
                }
            }
            val signature = BitString(privateKey.sign(unsignedBody.hash().toByteArray()))

            return CellBuilder.createCell {
                storeBits(signature)
                storeBits(unsignedBody.bits)
                storeRefs(unsignedBody.refs)
            }
        }

        fun createIntMsg(gift: WalletTransfer): MessageRelaxed<Cell> {
            val info = CommonMsgInfoRelaxed.IntMsgInfoRelaxed(
                    ihrDisabled = true,
                    bounce = gift.bounceable,
                    bounced = false,
                    src = AddrNone,
                    dest = gift.destination,
                    value = gift.coins,
                    ihrFee = Coins(),
                    fwdFee = Coins(),
                    createdLt = 0u,
                    createdAt = 0u
            )
            val init = Maybe.of(gift.messageData.stateInit?.let {
                Either.of<StateInit, CellRef<StateInit>>(null, it)
            })
            val bodyCell = gift.messageData.body
            val body = if (bodyCell.isEmpty()) {
                Either.of<Cell, CellRef<Cell>>(Cell.empty(), null)
            } else {
                Either.of<Cell, CellRef<Cell>>(null, CellRef(bodyCell))
            }
            return MessageRelaxed(
                info = info,
                init = init,
                body = body,
            )
        }
    }

    public suspend fun sendExternalMessage(liteApi: LiteApi, message: Cell): Int =
        liteApi(LiteServerSendMessage(BagOfCells(message).toByteArray().toByteString())).status

    public suspend fun runGetMethod(liteApi: LiteApi, method: String): SmartContractAnswer {
        val lastBlockId = liteApi(LiteServerGetMasterchainInfo).last
        val result = liteApi(
            LiteServerRunSmcMethod(
                mode = 4,
                id = lastBlockId,
                account = LiteServerAccountId(address.workchainId, address.address),
                methodId = LiteServerRunSmcMethod.methodId(method),
                params = LiteServerRunSmcMethod.params().toByteString()
            )
        )
        return SmartContractAnswer(
            success = result.exitCode == 0,
            stack = result.result?.let {
                VmStack.loadTlb(BagOfCells(it.toByteArray()).first())
            },
            exitCode = result.exitCode
        )
    }
}