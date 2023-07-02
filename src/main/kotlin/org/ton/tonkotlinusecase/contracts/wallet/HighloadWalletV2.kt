package org.ton.tonkotlinusecase.contracts.wallet

import org.ton.api.pk.PrivateKeyEd25519
import org.ton.bigint.BigInt
import org.ton.bitstring.BitString
import org.ton.block.*
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.buildCell
import org.ton.contract.wallet.MessageData
import org.ton.contract.wallet.WalletContract
import org.ton.contract.wallet.WalletTransfer
import org.ton.crypto.hex
import org.ton.hashmap.HashMapE
import org.ton.lite.api.LiteApi
import org.ton.lite.api.LiteApiClient
import org.ton.lite.client.LiteClient
import org.ton.tlb.CellRef
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.tlb.constructor.tlbCodec
import org.ton.tlb.storeTlb
import org.ton.tonkotlinusecase.constants.SendMode
import org.ton.tonkotlinusecase.toWalletTransfer
import org.ton.tonkotlinusecase.utcLongNow
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

class HighloadWalletV2(
    override val privateKey: PrivateKeyEd25519,
    override val workchain: Int = 0,
    override val subWalletId: Int = WalletContract.DEFAULT_WALLET_ID + workchain,
    liteClient: LiteClient
) : AbstractWallet(privateKey, workchain, subWalletId, liteClient) {

    fun generateQueryId(timeout: BigInt): BigInt {
        val random = BigInt.valueOf((Random.nextDouble() * 2.0.pow(30)).roundToLong())
        return (BigInt.valueOf(utcLongNow()) + timeout).shiftLeft(32).or(random)
    }

    override fun createDataInit(): Cell = CellBuilder.createCell {
        storeUInt(subWalletId, 32) // stored_subwallet
        storeUInt(0, 64) // last_cleaned
        storeBytes(privateKey.publicKey().key.toByteArray())
        storeTlb(HashMapE.tlbCodec(16, Cell.tlbCodec()), HashMapE.empty()) // old_queries
    }

    override val sourceCode: Cell = CODE

    companion object {

        // https://github.com/ton-blockchain/ton/blob/master/crypto/smartcont/highload-wallet-v2-code.fc
        val CODE = BagOfCells(
                hex("B5EE9C724101090100E5000114FF00F4A413F4BCF2C80B010201200203020148040501EAF28308D71820D31FD33FF823AA1F5320B9F263ED44D0D31FD33FD3FFF404D153608040F40E6FA131F2605173BAF2A207F901541087F910F2A302F404D1F8007F8E16218010F4786FA5209802D307D43001FB009132E201B3E65B8325A1C840348040F4438AE63101C8CB1F13CB3FCBFFF400C9ED54080004D03002012006070017BD9CE76A26869AF98EB85FFC0041BE5F976A268698F98E99FE9FF98FA0268A91040207A0737D098C92DBFC95DD1F140034208040F4966FA56C122094305303B9DE2093333601926C21E2B39F9E545A")
        ).first()

    }

    suspend fun transfer(liteApi: LiteApi, transfers: List<WalletTransfer>): Int {
        require(transfers.isNotEmpty() && transfers.size <= 254) { throw RuntimeException("wrong transfers size") }

        val message = createTransferMessage(
                address(), createStateInit(), transfers
        )
        return sendExternalMessage(liteApi, buildCell {
            storeTlb(Message.tlbCodec(AnyTlbConstructor), message)
        })
    }

    private fun createTransferMessage(
            address: AddrStd,
            stateInit: StateInit?,
            transfers: List<WalletTransfer>
    ): Message<Cell> {
        val info = ExtInMsgInfo(
                src = AddrNone,
                dest = address,
                importFee = Coins()
        )
        val maybeStateInit =
                Maybe.of(stateInit?.let { Either.of<StateInit, CellRef<StateInit>>(null, CellRef(it)) })

        val body = Either.of<Cell, CellRef<Cell>>(null, CellRef(createGiftBody(transfers)))
        return Message(
                info = info,
                init = maybeStateInit,
                body = body
        )
    }

    private fun createGiftBody(
            transfers: List<WalletTransfer>
    ): Cell {
        val unsignedBody = CellBuilder.createCell {
            storeInt(subWalletId, 32)
            storeInt(generateQueryId(BigInt.valueOf(60)), 64)
            storeTlb(
                    HashMapE.tlbCodec(16, WalletMessage.tlbCodec(AnyTlbConstructor)),
                    payloadToDict(
                            transfers.map {
                                WalletMessage(
                                        mode = it.sendMode,
                                        msg = createIntMsg(it)
                                )
                            }
                    )
            )
        }

        val signature = BitString(privateKey.sign(unsignedBody.hash().toByteArray()))

        return CellBuilder.createCell {
            storeBits(signature)
            storeBits(unsignedBody.bits)
            storeRefs(unsignedBody.refs)
        }
    }

    private fun <X: Any> payloadToDict(msgs: List<WalletMessage<X>>): HashMapE<WalletMessage<X>> {
        var hashMap = HashMapE.empty<WalletMessage<X>>()
        msgs.forEachIndexed { index, walletMessage ->
            val key = buildCell {
                storeInt(index, 16)
            }.bits
            hashMap = hashMap.set(key, walletMessage)
        }
        return hashMap
    }

    fun createDeployMessage() = Message(
            init = Maybe.of(
                    Either.of(null, CellRef(createStateInit())),
            ),
            body = Either.of(
                    null,
                    CellRef(CellBuilder.createCell {
                        storeUInt(subWalletId, 32)
                        storeUInt(generateQueryId(BigInt.valueOf(2).pow(16)), 64)
                        storeTlb(HashMapE.tlbCodec(16, Cell.tlbCodec()), HashMapE.empty())
                    })
            ),
            info = ExtInMsgInfo(
                    dest = address()
            )
    )

    suspend fun transfer(liteApi: LiteApiClient, address: String, amount: Long) {
        transfer(liteApi, listOf(Pair(address, amount)).toWalletTransfer())
    }

    suspend fun transfer(liteApi: LiteApiClient, address: String, amount: Long, comment: String) {
        transfer(liteApi, listOf(
            WalletTransfer {
                destination = AddrStd(address)
                coins = Coins.ofNano(amount)
                bounceable = false
                sendMode = SendMode.PAY_GAS_SEPARATELY
                messageData = MessageData.raw(
                    body = buildCell {
                        storeUInt(0, 32)
                        storeBytes(comment.toByteArray())
                    }
                )
            }
        ))
    }

}
