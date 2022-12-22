package org.ton.tonkotlinusecase

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.ton.block.AddrStd
import org.ton.tonkotlinusecase.contracts.LiteContract

@SpringBootTest
class GetMethodsTest: BaseTest() {

    @Autowired
    protected lateinit var liteContract: LiteContract

    @Test
    fun `get_nft_data of a single nft`() {
        runBlocking {
            val data = liteContract.getNftData(AddrStd("EQCDMCErrbb19GLCcKyxxhw9OjPBfstj3_zjh70vp1x_53_l"))!!
            println(
                data.ownerAddress.toAddrString()
            )

            assertEquals("EQCFWUtaHWMIAAfUaGPJVb5AuKZ9DBIOxIBi5u4WV0t0WBS8", data.ownerAddress.toAddrString())
        }
    }

    @Test
    fun `get collectable nft address by index`() {
        val index = 0
        val collectionAddress = AddrStd("EQAd8b25yIfvDScaMZWhF03mdUcfiyqbK_LoVK1fJ8QBqvQN")
        runBlocking {
            val nftAddress = liteContract.getItemAddressByIndex(collectionAddress, index)
            println(nftAddress?.toAddrString())

            assertEquals("EQB7R7t8aFIdSGEmw_Owvsx6NVqIFySJAgVMasa9zJis37Nr", nftAddress?.toAddrString())
        }
    }

    @Test
    fun `get collection data`() {
        val collectionAddress = AddrStd("EQAd8b25yIfvDScaMZWhF03mdUcfiyqbK_LoVK1fJ8QBqvQN")
        runBlocking {
            val collectionData = liteContract.getCollectionData(collectionAddress)
            println(collectionData?.owner?.toAddrString())
        }
    }
}