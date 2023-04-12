package org.ton.tonkotlinusecase.contracts

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.ton.api.tonnode.TonNodeBlockIdExt
import org.ton.bigint.BigInt
import org.ton.block.*
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.storeRef
import org.ton.lite.client.LiteClient
import org.ton.tlb.loadTlb
import org.ton.tonkotlinusecase.LiteServerAccountId
import org.ton.tonkotlinusecase.constants.ContractMethods
import org.ton.tonkotlinusecase.contracts.nft.AbstractNftContract
import org.ton.tonkotlinusecase.loadRemainingBits
import org.ton.tonkotlinusecase.loadRemainingBitsAll
import org.ton.tonkotlinusecase.toAddrString

/**
 * The main purpose of this class is to give access
 * to different GET methods, using explicit contract address.
 * Instead of default address inside each contract implementation.
 */
open class LiteContract(
    private val liteClient: LiteClient
) {

    suspend fun isContractDeployed(address: String): Boolean = isContractDeployed(AddrStd(address))

    suspend fun isContractDeployed(address: AddrStd): Boolean {
        return (liteClient.getAccountState(address).account.value as AccountInfo).storage.state is AccountActive
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

    companion object {

        data class AssetCollectionData (
            val nextIndex: Int,
            val content: AbstractNftContract.Companion.Content,
            val owner: MsgAddress
        )

        data class NftItem (
            val init: Boolean,
            val index: Int,
            val collectionAddress: MsgAddress?,
            val ownerAddress: MsgAddress,
            val content: AbstractNftContract.Companion.Content
        )

        fun packOffChainMetadata(
            collectionMetadataUrl: String,
            commonMetadataUrl: String
        ) = CellBuilder.createCell {
            storeRef {
                storeUInt(1, 8)
                storeBytes(collectionMetadataUrl.toByteArray())
            }
            storeRef{
                storeBytes(commonMetadataUrl.toByteArray())
            }
        }

        fun packOffChainMetadata(metadataUrl: String) = CellBuilder.createCell {
            storeUInt(1, 8)
            storeBytes(metadataUrl.toByteArray())
        }

        fun decodeNftFullContent(cell: Cell): AbstractNftContract.Companion.Content {
            require (cell.bits.isEmpty())
            val result = AbstractNftContract.Companion.Content(
                contentRaw = cell
            )
//            val c = loadTlb(FullContent)
//            val uri = String((((c as FullContent.OffChain).uri as Text).data as SnakeDataTail).bits.toByteArray())

            cell.parse {
                if (loadUInt(8) == BigInt.valueOf(0)) {
                    // process on-chain data
                    throw RuntimeException("on-chain processing not implemented")
                } else {
                    // process off-chain data
                    result.metadataUrl = String(loadRemainingBits().toByteArray())
                }
            }
            if (listOf(Char(0), Char(1)).contains(result.metadataUrl!![0]) && result.metadataUrl!!.contains("http") )
                result.metadataUrl = result.metadataUrl!!.takeLast(result.metadataUrl!!.length - 1)

            return result
        }

        fun decodeCollectableContent(cell: Cell): AbstractNftContract.Companion.Content {
            val result = AbstractNftContract.Companion.Content(
                contentRaw = cell
            )
            if (cell.refs.isEmpty()) return result

            cell.parse {
                loadRef().parse {
                    result.metadataUrl = String(loadRemainingBits().toByteArray())
                }
            }
            return result
        }

        fun decodeContent(cell: Cell): AbstractNftContract.Companion.Content {
            val result = AbstractNftContract.Companion.Content(
                contentRaw = cell
            )
            return if (cell.refs.isEmpty() && cell.bits.isEmpty())
                result
            else {
                var offchain = false
                if (!cell.bits.isEmpty()) {
                    cell.parse {
                        offchain = loadUInt(8) == BigInt.valueOf(1)
                        loadRemainingBitsAll()
                    }
                    cell.parse {
                        if (offchain) loadUInt(8)
                        result.metadataUrl = String(loadRemainingBitsAll().toByteArray())
                    }
                }
                if (cell.refs.isNotEmpty() && !cell.refs.first().bits.isEmpty()) {
                    result.metadataUrl = String(cell.refs.first().bits.toByteArray())
                }
                if ( (result.metadataUrl?.split("http")?.size ?: 0) > 1 )
                    result.metadataUrl = "http" + result.metadataUrl!!.split("http").drop(1).joinToString(separator = "")
                if (cell.bits.size == 8 || cell.bits.isEmpty())
                    result.metadataUrl = ""
                result
            }
        }

        fun decodeNftItem(stack: MutableVmStack): NftItem {
            val init = stack.popTinyInt() != 0L
            val index = stack.popTinyInt().toInt()
            val collectionAddress = stack.popSlice().loadTlb(MsgAddress)
            val ownerAddress = stack.popSlice().loadTlb(MsgAddress)

            val content = stack.popCell()
//            val fullContent = if (collectionAddress !is AddrNone)
//                decodeCollectableContent(content) else // only individual item should have TIP-64 layout
//                decodeNftFullContent(content)
            val fullContent = decodeContent(content)

            return NftItem(
                init = init,
                index = index,
                collectionAddress = if (collectionAddress is AddrNone) null else collectionAddress,
                ownerAddress = ownerAddress,
                content = fullContent
            )
        }
    }

    protected val logger = LoggerFactory.getLogger(this::class.simpleName)

}
