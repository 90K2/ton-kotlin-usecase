package org.ton.tonkotlinusecase

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.block.AddrStd
import org.ton.lite.client.LiteClient
import org.ton.mnemonic.Mnemonic
import org.ton.tonkotlinusecase.contracts.LiteContract
import org.ton.tonkotlinusecase.contracts.nft.AbstractNftContract
import org.ton.tonkotlinusecase.contracts.nft.NFTV1
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
            val keyPair = Mnemonic.toKeyPair(seedPhrase)
            val privateKey = PrivateKeyEd25519(keyPair.second)
            WalletV4R2(liteApi = liteClient.liteApi, privateKey = privateKey)
        }
    }

    val NFT_DEPLOY_PRICE = 0.073.toNano()

    @Test
    fun `get wallet seqno`() {
        runBlocking {
            val wallet = getWallet()
            println(
                wallet.seqno()
            )
        }
    }

    @Test
    fun `send ton`() {
        runBlocking {
            val wallet = getWallet()
            val myFriendAddress = AddrStd("kQC7ryRspjsMrdnuuA98uGyLMUVd_dDWjroYLBHoIz9ovl7e")
            val tonGift = 0.1

            wallet.sendInternal(
                dest = myFriendAddress,
                value = tonGift.toNano()
            )

        }
    }

    @Test
    fun `deploy nft`() {
        runBlocking {
            val wallet = getWallet()

            val nftOwner = wallet.address()
            val contract = NFTV1(
                liteClient = liteClient,
                ownerAddress = nftOwner,
                metadataURL = "https://your.domain.com/awesome_nft.json"
            )

            // correct metadata.json content example : https://assets.tonplay.io/assets/ton-chess/md/AvatarsMain.json

            if (liteContract.isContractDeployed(contract.address()))
                println("Already initialized")
            else {
                wallet.sendInternal(
                    contract.address(), NFT_DEPLOY_PRICE, true, null, contract.createStateInit()
                )
            }

        }
    }

    @Test
    fun `send nft`() {
        runBlocking {
            val wallet = getWallet()

            val nftAddress = AddrStd("EQBu5TtSV8bnD6w_7N5OfiwWHolqoPoqWXcN4etsqOZyvjv5")
            val newOwner = AddrStd("kQC7ryRspjsMrdnuuA98uGyLMUVd_dDWjroYLBHoIz9ovl7e")

            wallet.sendInternal(
                dest = nftAddress,
                value = NFT_DEPLOY_PRICE, // 0.05 for minimum balance + gas + forward
                body = AbstractNftContract.packTransferOwnership(
                    newOwner = newOwner,
                    forwardAmount = 0.02.toNano()
                )
            )
        }
    }
}
