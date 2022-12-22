package org.ton.tonkotlinusecase

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.ton.block.AccountActive
import org.ton.lite.client.LiteClient
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
        val lastMCblock = runBlocking {
            liteClient.getLastBlockId()
        }
        val seqno = lastMCblock.seqno
        println(seqno)
    }

    @Test
    fun `get account info raw`() {
        runBlocking {
            val data = liteClient.getAccount(walletAddress)

            assertNotNull(data?.storage?.state)
            assertTrue(data?.storage?.state is AccountActive)
        }
    }

    @Test
    fun `get account info object`() {
        runBlocking {
            val data = tonMapper.toAccountDTO(
                liteClient.getAccount(walletAddress)
            )

            assertNotNull(data)
            assertEquals(walletAddress, data!!.address)
        }
    }

    @Test
    fun `read ton transfer with comment`() {
        runBlocking {

            val r = tonClient.loadBlockTransactions(liteClient, 0, 30756696)

            // Receiving TONs with comment from Wallet Bot https://ton.cx/tx/31630224000003:yU0l55aqOp4RMf+Vd+QnJ8XXWkwWV8UfsHvsnmftR4s=:EQDKe51uyQ_SKhrdqP5uCBMMcUOJMvvFUEy4q9BLGXeXApPc
            val tx = r.firstOrNull { it.hash == "b9dd788e21d8659b63976314251338ba8c69230757d54369a3e248fdbc659d41" }

            assertEquals("Get 50 TON from TON Foundation https://ton.events/airdrop", tx?.inMsg?.comment)
            assertEquals(TonMsgAction.TRANSFER, tx?.inMsg?.msgAction)
        }
    }

    @Test
    fun `read notification about new nft`() {
        runBlocking {
            val r = tonClient.loadBlockTransactions(liteClient, 0, 26584176)

            // Notify about receiving  NFT from EQC6OFk_mw0qz4kw10A8_4W9qbQIxojHfGyLtWJ-vxITPh3t
            // https://ton.cx/tx/28776812000005:diL2hpETxFfIrp2Tr9fOW2+MwiWclm91TCIDFlTRjHY=:EQDcUJhURuwR5mWAf5iYOw2TZ4CJm0gWRMo0laWRu20eSnQv
            val tx = r.firstOrNull { it.hash == "7622f6869113c457c8ae9d93afd7ce5b6f8cc2259c966f754c22031654d18c76" }

            assertTrue(tx?.inMsg?.op?.toInt() == OpCodes.OP_NFT_OWNERSHIP_ASSIGNED)
        }
    }
}