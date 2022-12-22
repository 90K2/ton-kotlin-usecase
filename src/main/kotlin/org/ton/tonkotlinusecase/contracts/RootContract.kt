package org.ton.tonkotlinusecase.contracts

import org.slf4j.LoggerFactory
import org.ton.bigint.BigInt
import org.ton.block.*
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.storeRef
import org.ton.contract.Contract
import org.ton.lite.api.LiteApi
import org.ton.lite.api.liteserver.LiteServerSendMsgStatus
import org.ton.tlb.loadTlb
import org.ton.tlb.storeTlb
import org.ton.tonkotlinusecase.contracts.nft.AbstractNftContract
import org.ton.tonkotlinusecase.loadRemainingBits
import org.ton.tonkotlinusecase.loadRemainingBitsAll

abstract class RootContract(
    override val liteApi: LiteApi,
    override val workchainId: Int = 0
) : Contract {

    protected val logger = LoggerFactory.getLogger(this::class.simpleName)

    override fun createDataInit(): Cell {
        TODO("Not yet implemented")
    }

    override fun createExternalInitMessage(): Message<Cell> {
        TODO("Not yet implemented")
    }

    override suspend fun deploy(): LiteServerSendMsgStatus {
        TODO("Not yet implemented")
    }


    fun packStateInit(stateInit: StateInit) = CellBuilder.createCell {
        storeTlb(StateInit.tlbCodec(), stateInit)
    }

    override fun toString(): String {
        TODO("Not yet implemented")
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
            storeRef{storeBytes(commonMetadataUrl.toByteArray())}
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
                if (loadUInt(8) == BigInt(0)) {
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
                if (cell.bits.isNotEmpty()) {
                    cell.parse {
                        offchain = loadUInt(8) == BigInt(1)
                        loadRemainingBitsAll()
                    }
                    cell.parse {
                        if (offchain) loadUInt(8)
                        result.metadataUrl = String(loadRemainingBitsAll().toByteArray())
                    }
                }
                if (cell.refs.isNotEmpty() && cell.refs.first().bits.isNotEmpty()) {
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
}