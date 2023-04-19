package org.ton.tonkotlinusecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.ton.api.liteclient.config.LiteClientConfigGlobal
import org.ton.api.liteserver.LiteServerDesc
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AccountActive
import org.ton.block.AccountInfo
import org.ton.block.AddrStd
import org.ton.crypto.encoding.base64
import org.ton.lite.client.LiteClient
import org.ton.tl.ByteString.Companion.toByteString
import org.ton.tonkotlinusecase.client.TonClient
import org.ton.tonkotlinusecase.constants.OpCodes
import org.ton.tonkotlinusecase.dto.TonMsgAction
import org.ton.tonkotlinusecase.mapper.TonMapper

@SpringBootTest
class LiteClientTests: BaseTest() {

    @Autowired
    private lateinit var liteClient: LiteClient

    @Autowired
    private lateinit var tonMapper: TonMapper

    @Autowired
    private lateinit var tonClient: TonClient

    val walletAddress = "EQAs87W4yJHlF8mt29ocA4agnMrLsOP69jC1HPyBUjJay-7l"

    @Test
    fun `get last masterchain block`() {
        val lastMCblockId = runBlocking {
            liteClient.getLastBlockId()
        }
        val seqno = lastMCblockId.seqno
        println(seqno)
        assertTrue(seqno > 0)

        val lastBlock = runBlocking {
            liteClient.getBlock(lastMCblockId)
        }

        assertTrue(lastBlock?.info != null )
    }

    @Test
    fun `get account info raw`() {
        val liteClient = LiteClient(
            liteClientConfigGlobal = LiteClientConfigGlobal(
                liteServers = listOf(
                    LiteServerDesc(id = PublicKeyEd25519(
                        base64("n4VDnSCUuSpjnCyUk9e3QOOd6o0ItSWYbTnW3Wnn8wk=").toByteString()
                    ), ip = 84478511, port = 19949)
                )
            ),
            coroutineContext = Dispatchers.Default
        )
        runBlocking {
            val data = tonClient.getAccount("EQAs87W4yJHlF8mt29ocA4agnMrLsOP69jC1HPyBUjJay-7l")

            // assume that active account is OK
            assertTrue(data is AccountInfo)
            assertNotNull((data as AccountInfo).storage.state)
            assertTrue(data.storage.state is AccountActive)

            // inactive account
            // Can't deserialize account state
            // java.lang.IllegalStateException: Can't deserialize account state
            //	at org.ton.lite.client.LiteClient.getAccountState(LiteClient.kt:347)
//            val data2 = tonClient.getAccount("EQDoFjaqMtuxkJV-caGiEdxMxjkTAWU9oskjpfAA1uwHbbPr")
            val data2 = tonClient.getAccount("EQCGU_793GU8o4MucRH2-gUfJLWrTMIjpXQxrNo8w7sKTzfq")

            // assume that uninitialized account is OK
            assertTrue(data2?.storage?.state == null)
        }
    }

    @Test
    fun `get account info object`() {
        runBlocking {
            val data = tonMapper.toAccountDTO(
                tonClient.getAccount(walletAddress) as AccountInfo
            )

            assertNotNull(data)
            assertEquals(walletAddress, data!!.address)
        }
    }

    @Test
    fun `read ton transfer with comment`() {
        runBlocking {

            val r = tonClient.loadBlockTransactions(liteClient, 0, 34368151)

            // https://ton.cx/tx/36779786000003:bakFy0AKcIysTKaP2VNvQw1PU2cTphWbcmhOmWjUm1A=:EQD5JYfff8SH8xxAEFE0-rouw7T8e1JyKIS96LynloPNcYod
            val tx = r.firstOrNull { it.hash == "6da905cb400a708cac4ca68fd9536f430d4f536713a6159b72684e9968d49b50" }

            assertEquals("#1580 [Both are even] t.me/KubikiGame", tx?.inMsg?.comment)
            assertEquals(TonMsgAction.TRANSFER, tx?.inMsg?.msgAction)
        }
    }

    @Test
    fun `read notification about new nft`() {
        runBlocking {
            val r = tonClient.loadBlockTransactions(liteClient, 0, 34368174)

            // https://ton.cx/tx/36779810000007:404XwwvLLM7tdIEYOb9iLYXtdjU9r2XEufuK1YyOJfs=:EQDISS1kC3hW5NSL2xOE8U9XWXLGGnoPCrFX3zRJLQZ67f06
            val tx = r.firstOrNull { it.hash == "e34e17c30bcb2cceed74811839bf622d85ed76353daf65c4b9fb8ad58c8e25fb" }

            assertTrue(tx?.inMsg?.op?.toInt() == OpCodes.OP_NFT_OWNERSHIP_ASSIGNED)
        }
    }

    @Test
    fun `get address transactions`() {
        val address = "EQCSP1xhpNpQmIIYDwbMs2lZmy6ep496v3JfIy__DTH6ZqJ2"
        runBlocking {
            val account = liteClient.getAccountState(AddrStd(address))

            val txs = (account.lastTransactionId?.let {
                liteClient.getTransactions(
                    accountAddress = AddrStd(address),
                    fromTransactionId = account.lastTransactionId!!,
                    count = 10
                )
            } ?: listOf()).map {
                tonMapper.mapTx(it.transaction.value, it.blockId.seqno, it.blockId.workchain)
            }
            println(txs)
        }

    }
}
