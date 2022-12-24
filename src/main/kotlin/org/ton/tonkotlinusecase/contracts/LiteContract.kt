package org.ton.tonkotlinusecase.contracts

import kotlinx.coroutines.delay
import org.ton.api.tonnode.TonNodeBlockIdExt
import org.ton.block.*
import org.ton.cell.Cell
import org.ton.lite.api.liteserver.LiteServerAccountId
import org.ton.lite.client.LiteClient
import org.ton.tlb.loadTlb
import org.ton.tonkotlinusecase.constants.ContractMethods
import org.ton.tonkotlinusecase.contracts.nft.AbstractNftContract
import org.ton.tonkotlinusecase.toAddrString

/**
 * The main purpose of this class is to give access
 * to different GET methods, using explicit contract address.
 * Instead of default address inside each contract implementation.
 */
open class LiteContract(
    private val liteClient: LiteClient
): RootContract(liteClient.liteApi) {
    override val code: Cell
        get() = TODO("Not yet implemented")
    override val name: String
        get() = TODO("Not yet implemented")

    suspend fun isContractDeployed(address: String): Boolean {
        return (liteClient.getAccount(LiteServerAccountId(AddrStd(address))) as AccountInfo)?.storage?.state is AccountActive
    }

    suspend fun isContractDeployed(address: AddrStd): Boolean {
        return (liteClient.getAccount(LiteServerAccountId(address)) as AccountInfo)?.storage?.state is AccountActive
    }

    private suspend fun runSmcRetry(
        address: AddrStd,
        method: String,
        lastBlockId: TonNodeBlockIdExt? = null,
        params: List<VmStackValue> = listOf()
    ): VmStack {
        return if (lastBlockId != null && params.isNotEmpty())
            liteClient.runSmcMethod(
                address = LiteServerAccountId(address),
                methodName = method,
                blockId = lastBlockId,
                params = params
            )
        else if (lastBlockId != null)
            liteClient.runSmcMethod(address = LiteServerAccountId(address), methodName = method, blockId = lastBlockId)
        else if (params.isNotEmpty())
            liteClient.runSmcMethod(address = LiteServerAccountId(address), methodName = method, params = params)
        else
            liteClient.runSmcMethod(address = LiteServerAccountId(address), methodName = method)
    }

    // I do not use AOP or spring-retry because of current class-architecture
    // we need to make retryable calls from other class that must be injected here
    // but this class is the Root of all contracts, so it will not be very handful
    // --
    // also for make aspects work we need to make everything open: logger, liteClient etc
    // probably liteClient must be removed from here somehow at all
    protected suspend fun runSmc(
        address: AddrStd,
        method: String,
        lastBlockId: TonNodeBlockIdExt? = null,
        params: List<VmStackValue> = listOf()
    ): VmStack? {
        var result: VmStack? = null
        var retryCount = 0
        var ex: Exception? = null
        while (result == null && retryCount < 4) {
            if (retryCount > 0) {
                delay(100)
//                println("Retry $retryCount $method for ${address.toAddrString()}")
            }
            try {
                result = runSmcRetry(address, method, lastBlockId, params)
            } catch (e: Exception) {
                ex = e
            }
            retryCount++
        }
        if (result == null && ex != null) {
            logger.warn("Error in $method for ${address.toAddrString()}")
            logger.warn(ex.message)
        }
//        }.onFailure {
//            logger.warn("Error in $method for ${address.toAddrString()}")
//            logger.warn(it.message)
//        }.getOrNull()

        return result
    }


    suspend fun getCollectableContent(collectionAddress: AddrStd, index: Int): AbstractNftContract.Companion.Content? {
        return getCollectableContent(collectionAddress, index, Cell())
    }

    // ==== COLLECTION METHODS ====

    suspend fun getItemAddressByIndex(
        collectionAddress: AddrStd,
        index: Int,
        lastBlockId: TonNodeBlockIdExt? = null
    ): MsgAddress? {
        return runSmc(
            collectionAddress,
            ContractMethods.getItemAddressByIndex,
            params = listOf(VmStackValue.of(index)),
            lastBlockId = lastBlockId
        )?.let {
            val stack = it.toMutableVmStack()
            return stack.popSlice().loadTlb(MsgAddress)
        }
    }

    suspend fun getCollectionData(
        address: AddrStd,
        lastBlockId: TonNodeBlockIdExt? = null
    ): Companion.AssetCollectionData? {
        return runSmc(address, ContractMethods.getCollectionData, lastBlockId = lastBlockId)?.let {
            val stack = it.toMutableVmStack()
            return Companion.AssetCollectionData(
                nextIndex = stack.popInt().toInt(),
                content = decodeContent(stack.popCell()),
                owner = stack.popSlice().loadTlb(MsgAddress)
            )
        }
    }

    /**
     * Content.metadataUrl - collection common metadata url
     *
     */
    suspend fun getCollectableContent(
        address: AddrStd,
        index: Int,
        individualContent: Cell,
        lastBlockId: TonNodeBlockIdExt? = null
    ): AbstractNftContract.Companion.Content? {
        return runSmc(
            address,
            ContractMethods.getCollectableContent,
            params = listOf(VmStackValue.of(index), VmStackValue.of(individualContent)),
            lastBlockId = lastBlockId
        )?.let {
            val stack = it.toMutableVmStack()
            val raw = stack.popCell()
            val content = decodeContent(raw)
            return AbstractNftContract.Companion.Content(
                metadataUrl = content.metadataUrl,
                contentRaw = raw
            )
        }
    }
    // ==== NFT METHODS ====

    suspend fun getNftData(address: AddrStd, lastBlockId: TonNodeBlockIdExt? = null): Companion.NftItem? {
        return runSmc(address, ContractMethods.getNftData, lastBlockId)?.let { stack ->
            if (stack.depth != 5) {
                throw RuntimeException("Invalid stack data")
            }
            decodeNftItem(stack.toMutableVmStack())
        }
    }

}
