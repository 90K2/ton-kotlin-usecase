package org.ton.tonkotlinusecase

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.cell.buildCell
import org.ton.contract.wallet.MessageData
import org.ton.contract.wallet.WalletTransfer
import org.ton.lite.client.LiteClient
import org.ton.mnemonic.Mnemonic
import org.ton.tonkotlinusecase.constants.SendMode
import org.ton.tonkotlinusecase.contracts.wallet.HighloadWalletV2
import org.ton.tonkotlinusecase.contracts.LiteContract
import org.ton.tonkotlinusecase.contracts.wallet.WalletV4R2

@SpringBootTest
class WalletTests: BaseTest() {

    @Autowired
    private lateinit var liteClient: LiteClient

    @Autowired
    private lateinit var liteContract: LiteContract

    @Value("\${ton.wallet.mnemonic}")
    private lateinit var seedPhrase: Array<String>

    private fun getWallet(): WalletV4R2 {
        return runBlocking {
//            val m = Mnemonic.generate()
//            println(m)
            val m = listOf(
                "fringe", "slice", "follow", "increase", "lottery", "best", "minute", "speak", "quick", "cat", "happy", "report",
                "garment", "novel", "dawn", "acid", "update", "cricket", "lecture", "tribe", "urban", "media", "enforce", "shop"
            )
            val keyPair = Mnemonic.toKeyPair(m)

            val privateKey = PrivateKeyEd25519(keyPair.second)
            WalletV4R2(0, privateKey, liteClient)
        }
    }

    val NFT_DEPLOY_PRICE = 0.073.toNano()

    @Test
    fun `get wallet seqno`() {
        runBlocking {
            val wallet = getWallet()
            println(wallet.address.toAddrString())
            println(
                wallet.getSeqno()
            )
        }
    }

    @Test
    fun `highload wallet`() {
        val seed = seedPhrase.toList()
        val privateKey = PrivateKeyEd25519(Mnemonic.toSeed(seed))

        val w = HighloadWalletV2(privateKey = privateKey, liteClient = liteClient)

        assertEquals("EQB8GHeD29YFlSkgqvPfXEAqpyq_1IRiqRbD3E5zp6djSDqt", w.address().toAddrString())

        val targets = listOf(
            AddrStd("EQDKe51uyQ_SKhrdqP5uCBMMcUOJMvvFUEy4q9BLGXeXApPc")
        )
        runBlocking {
            w.transfer(liteClient.liteApi, targets.map {
                WalletTransfer {
                    destination = it
                    coins = Coins.ofNano(0.000001.toNano())
                    bounceable = true
                    messageData = MessageData.raw(
                        body = buildCell {
                            storeUInt(0, 32)
                            storeBytes("Comment".toByteArray())
                        },
                        stateInit = null
                    )
                    sendMode = SendMode.PAY_GAS_SEPARATELY
                }
            })
        }
    }

    @Test
    fun `send ton`() {
        runBlocking {
            val wallet = getWallet()
            val myFriendAddress = "kQC7ryRspjsMrdnuuA98uGyLMUVd_dDWjroYLBHoIz9ovl7e"
            val tonGift = 0.001.toNano()

            runBlocking {
                wallet.transfer(myFriendAddress, tonGift)
            }

        }
    }

//    @Test
//    fun `deploy nft`() {
//        runBlocking {
//            val wallet = getWallet()
//
//            val nftOwner = wallet.address()
//            val contract = NFTV1(
//                liteClient = liteClient,
//                ownerAddress = nftOwner,
//                metadataURL = "https://your.domain.com/awesome_nft.json"
//            )
//
//            // correct metadata.json content example : https://assets.tonplay.io/assets/ton-chess/md/AvatarsMain.json
//
//            if (liteContract.isContractDeployed(contract.address()))
//                println("Already initialized")
//            else {
//                wallet.sendInternal(
//                    contract.address(), NFT_DEPLOY_PRICE, true, null, contract.createStateInit()
//                )
//            }
//
//        }
//    }
//
//    @Test
//    fun `send nft`() {
//        runBlocking {
//            val wallet = getWallet()
//
//            val nftAddress = AddrStd("EQBu5TtSV8bnD6w_7N5OfiwWHolqoPoqWXcN4etsqOZyvjv5")
//            val newOwner = AddrStd("kQC7ryRspjsMrdnuuA98uGyLMUVd_dDWjroYLBHoIz9ovl7e")
//
//            wallet.sendInternal(
//                dest = nftAddress,
//                value = NFT_DEPLOY_PRICE, // 0.05 for minimum balance + gas + forward
//                body = AbstractNftContract.packTransferOwnership(
//                    newOwner = newOwner,
//                    forwardAmount = 0.02.toNano()
//                )
//            )
//        }
//    }
}
