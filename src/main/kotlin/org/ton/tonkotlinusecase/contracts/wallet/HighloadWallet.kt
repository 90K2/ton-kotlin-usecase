package org.ton.tonkotlinusecase.contracts.wallet

import org.ton.api.pk.PrivateKeyEd25519
import org.ton.bigint.BigInt
import org.ton.bitstring.BitString
import org.ton.block.*
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.buildCell
import org.ton.contract.wallet.HighLoadWalletV2Contract
import org.ton.contract.wallet.WalletContract
import org.ton.contract.wallet.WalletMessage
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
import org.ton.tonkotlinusecase.contracts.SmartContract
import org.ton.tonkotlinusecase.toWalletTransfer
import org.ton.tonkotlinusecase.utcLongNow
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

class HighloadWallet(
    val privateKey: PrivateKeyEd25519,
    override val workchain: Int = 0,
    val subWalletId: Int = WalletContract.DEFAULT_WALLET_ID + workchain,
    override val liteClient: LiteClient
): SmartContract(liteClient, workchain) {

    override val CODE = BagOfCells(
        hex("B5EE9C724101090100E5000114FF00F4A413F4BCF2C80B010201200203020148040501EAF28308D71820D31FD33FF823AA1F5320B9F263ED44D0D31FD33FD3FFF404D153608040F40E6FA131F2605173BAF2A207F901541087F910F2A302F404D1F8007F8E16218010F4786FA5209802D307D43001FB009132E201B3E65B8325A1C840348040F4438AE63101C8CB1F13CB3FCBFFF400C9ED54080004D03002012006070017BD9CE76A26869AF98EB85FFC0041BE5F976A268698F98E99FE9FF98FA0268A91040207A0737D098C92DBFC95DD1F140034208040F4966FA56C122094305303B9DE2093333601926C21E2B39F9E545A")
    ).first()

    override fun createDataInit(): Cell = buildCell {
        storeUInt(subWalletId, 32) // stored_subwallet
        storeUInt(0, 64) // last_cleaned
        storeBytes(privateKey.publicKey().key.toByteArray())
        storeTlb(HashMapE.tlbCodec(16, Cell.tlbCodec()), HashMapE.empty()) // old_queries
    }

    private fun generateQueryId(timeout: BigInt): BigInt {
        val random = BigInt.valueOf((Random.nextDouble() * 2.0.pow(30)).roundToLong())
        return (BigInt.valueOf(utcLongNow()) + timeout).shiftLeft(32).or(random)
    }

    val wallet = HighLoadWalletV2Contract(
        workchain = workchain,
        init = createStateInit()
    )

    suspend fun transfer(transfers: List<WalletTransfer>) {
        require(transfers.isNotEmpty() && transfers.size <= 254) { throw RuntimeException("wrong transfers size") }

        val message = createTransferMessage(
            this.wallet.address as AddrStd, createStateInit(), transfers
        )
        this.wallet.sendExternalMessage(
            liteClient.liteApi, AnyTlbConstructor, message
        )

        val queryPayload = HighLoadWalletV2Contract.queryPayload(
            subWalletId = subWalletId,
            queryId = generateQueryId(BigInt.valueOf(60)).toLong(),
            msgs = payloadToDict(
                transfers.map {
                    WalletMessage(
                        mode = it.sendMode,
                        msg = createIntMsg(it)
                    )
                }
            )
        )
//        this.wallet.sendQuery(
//            liteApi, AnyTlbConstructor, HighLoadWalletV2Contract.query(
//                signature = BitString(privateKey.sign(.hash().toByteArray())),
//                payload = queryPayload
//        ))
    }


    fun createTransferMessage(
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
        val init = Maybe.of(gift.stateInit?.let {
            Either.of<StateInit, CellRef<StateInit>>(null, CellRef(it))
        })
        val body = if (gift.body == null) {
            Either.of<Cell, CellRef<Cell>>(Cell(), null)
        } else {
            Either.of<Cell, CellRef<Cell>>(null, CellRef(gift.body!!))
        }

        return MessageRelaxed(
            info = info,
            init = init,
            body = body,
        )
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

    suspend fun transfer(address: String, amount: Long) {
        transfer(listOf(Pair(address, amount)).toWalletTransfer())
    }

    override val address: MsgAddressInt
        get() = TODO("Not yet implemented")
    override val state: AccountState
        get() = TODO("Not yet implemented")

    override fun loadData(): Any? {
        TODO("Not yet implemented")
    }
}
