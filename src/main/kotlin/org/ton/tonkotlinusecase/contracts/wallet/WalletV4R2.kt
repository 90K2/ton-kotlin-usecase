package org.ton.tonkotlinusecase.contracts.wallet

import org.ton.api.pk.PrivateKeyEd25519
import org.ton.bitstring.BitString
import org.ton.block.*
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.buildCell
import org.ton.contract.SnakeData
import org.ton.contract.wallet.MessageData
import org.ton.contract.wallet.WalletContract
import org.ton.contract.wallet.WalletTransfer
import org.ton.crypto.encoding.base64
import org.ton.lite.client.LiteClient
import org.ton.tlb.CellRef
import org.ton.tlb.constructor.AnyTlbConstructor
import org.ton.cell.storeRef
import org.ton.tlb.storeTlb
import org.ton.tonkotlinusecase.constants.SendMode
import org.ton.tonkotlinusecase.toSnakeData
import org.ton.tonkotlinusecase.toWalletTransfer


class WalletV4R2(
    privateKey: PrivateKeyEd25519,
    workchain: Int = 0,
    subWalletId: Int = WalletContract.DEFAULT_WALLET_ID + workchain,
    liteClient: LiteClient
): AbstractWallet(privateKey, workchain, subWalletId, liteClient) {


    override fun createDataInit() = CellBuilder.createCell {
        storeUInt(0, 32) // seqno
        storeUInt(subWalletId, 32)
        storeBytes(privateKey.publicKey().key.toByteArray())
        storeBit(false) // plugins
    }

    override val sourceCode: Cell = BagOfCells(base64("te6cckECFAEAAtQAART/APSkE/S88sgLAQIBIAIDAgFIBAUE+PKDCNcYINMf0x/THwL4I7vyZO1E0NMf0x/T//QE0VFDuvKhUVG68qIF+QFUEGT5EPKj+AAkpMjLH1JAyx9SMMv/UhD0AMntVPgPAdMHIcAAn2xRkyDXSpbTB9QC+wDoMOAhwAHjACHAAuMAAcADkTDjDQOkyMsfEssfy/8QERITAubQAdDTAyFxsJJfBOAi10nBIJJfBOAC0x8hghBwbHVnvSKCEGRzdHK9sJJfBeAD+kAwIPpEAcjKB8v/ydDtRNCBAUDXIfQEMFyBAQj0Cm+hMbOSXwfgBdM/yCWCEHBsdWe6kjgw4w0DghBkc3RyupJfBuMNBgcCASAICQB4AfoA9AQw+CdvIjBQCqEhvvLgUIIQcGx1Z4MesXCAGFAEywUmzxZY+gIZ9ADLaRfLH1Jgyz8gyYBA+wAGAIpQBIEBCPRZMO1E0IEBQNcgyAHPFvQAye1UAXKwjiOCEGRzdHKDHrFwgBhQBcsFUAPPFiP6AhPLassfyz/JgED7AJJfA+ICASAKCwBZvSQrb2omhAgKBrkPoCGEcNQICEekk30pkQzmkD6f+YN4EoAbeBAUiYcVnzGEAgFYDA0AEbjJftRNDXCx+AA9sp37UTQgQFA1yH0BDACyMoHy//J0AGBAQj0Cm+hMYAIBIA4PABmtznaiaEAga5Drhf/AABmvHfaiaEAQa5DrhY/AAG7SB/oA1NQi+QAFyMoHFcv/ydB3dIAYyMsFywIizxZQBfoCFMtrEszMyXP7AMhAFIEBCPRR8qcCAHCBAQjXGPoA0z/IVCBHgQEI9FHyp4IQbm90ZXB0gBjIywXLAlAGzxZQBPoCFMtqEssfyz/Jc/sAAgBsgQEI1xj6ANM/MFIkgQEI9Fnyp4IQZHN0cnB0gBjIywXLAlAFzxZQA/oCE8tqyx8Syz/Jc/sAAAr0AMntVGliJeU=")).first()

    override val address = address()


    suspend fun transfer(address: String, amount: Long) {
        transfer(transfers = listOf(Pair(address, amount)).toWalletTransfer().toTypedArray())
    }

    private suspend fun transfer(
        seqno: Int,
        vararg transfers: WalletTransfer
    ) {
        val message = createTransferMessage(
            address = address as AddrStd,
            stateInit = if (seqno == 0) createStateInit() else null,
            privateKey = privateKey,
            validUntil = Int.MAX_VALUE,
            walletId = this.subWalletId,
            seqno = seqno,
            transfers = transfers
        )
        sendExternalMessage(liteClient.liteApi, buildCell {
            storeTlb(Message.tlbCodec(AnyTlbConstructor), message)
        })
    }

    suspend fun transfer(address: String, amount: Long, comment: String) {
        transfer(transfers = listOf(
            WalletTransfer {
                destination = AddrStd(address)
                coins = Coins.ofNano(amount)
                bounceable = false
                sendMode = SendMode.PAY_GAS_SEPARATELY
                messageData = MessageData.raw(
                    body = buildCell {
                        storeUInt(0, 32)
                        storeRef {
                            storeTlb(SnakeData, comment.toByteArray(Charsets.UTF_8).toSnakeData())
                        }
                    }
                )
            }
        ).toTypedArray())
    }


    private fun createTransferMessage(
        address: MsgAddressInt,
        stateInit: StateInit?,
        privateKey: PrivateKeyEd25519,
        walletId: Int,
        validUntil: Int,
        seqno: Int,
        vararg transfers: WalletTransfer
    ): Message<Cell> {
        val info = ExtInMsgInfo(
            src = AddrNone,
            dest = address,
            importFee = Coins()
        )
        val maybeStateInit =
            Maybe.of(stateInit?.let { Either.of<StateInit, CellRef<StateInit>>(null, CellRef(it)) })
        val transferBody = createTransferMessageBody(
            privateKey,
            walletId,
            validUntil,
            seqno,
            *transfers
        )
        val body = Either.of<Cell, CellRef<Cell>>(null, CellRef(transferBody))
        return Message(
            info = info,
            init = maybeStateInit,
            body = body
        )
    }

    private fun createTransferMessageBody(
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

    private fun createIntMsg(gift: WalletTransfer): MessageRelaxed<Cell> {
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
        val body = if (gift.messageData.body.isEmpty()) {
            Either.of<Cell, CellRef<Cell>>(Cell.empty(), null)
        } else {
            Either.of<Cell, CellRef<Cell>>(null, CellRef(gift.messageData.body))
        }

        return MessageRelaxed(
            info = info,
            init = init,
            body = body,
        )
    }
}