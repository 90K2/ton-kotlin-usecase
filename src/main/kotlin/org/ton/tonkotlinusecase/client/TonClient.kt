package org.ton.tonkotlinusecase.client

import org.springframework.stereotype.Component
import org.ton.api.tonnode.TonNodeBlockId
import org.ton.api.tonnode.TonNodeBlockIdExt
import org.ton.bigint.BigInt
import org.ton.block.*
import org.ton.lite.client.LiteClient
import org.ton.tonkotlinusecase.dto.TonTxDTO
import org.ton.tonkotlinusecase.dto.TonTxRawDTO
import org.ton.tonkotlinusecase.mapper.TonMapper


@Component
class TonClient(
    private val liteClient: LiteClient,
    private val tonMapper: TonMapper
) {

    /**
     * @address :base64 user friendly address string
     */
    suspend fun getAccount(address: String): AccountInfo? = getAccount(AddrStd(address))

    suspend fun getAccount(address: AddrStd): AccountInfo? {
        val account = liteClient.getAccountState(address).account.value
        return when (account) {
                is AccountInfo -> account as AccountInfo
                is AccountNone -> null
                else -> null
        }
    }

    /**
     * wc = -1 ;; master bock
     * wc = 0 ;; shard block
     */
    suspend fun loadBlockTransactions(liteClient: LiteClient, wc: Int, seqno: Int): List<TonTxDTO> {
        val block = liteClient.lookupBlock(TonNodeBlockId(
            workchain = wc,
            shard = Long.MIN_VALUE,
            seqno = seqno
        )) ?: throw RuntimeException("Cannot find block $wc:$seqno")

        return collectTransactions(liteClient.getBlock(block)!!, wc, liteClient)
            .map { tonMapper.mapTx(it.tx, it.blockId, it.wc) }
    }

    suspend fun loadBlockTransactions(liteClient: LiteClient, block: Block?, wc: Int): MutableList<TonTxRawDTO> {
        if (block == null) return mutableListOf()
        return collectTransactions(block, wc, liteClient)
    }

    private fun getBlockId(workchain: Int, descr: ShardDescrNew) = TonNodeBlockIdExt(
        workchain = workchain,
        shard = descr.nextValidatorShard.toLong(),
        seqno = descr.seqNo.toInt(),
        rootHash = descr.rootHash.toByteArray(),
        fileHash = descr.fileHash.toByteArray()
    )


    suspend fun collectTransactions(block: Block, blockWc: Int, liteClient: LiteClient): MutableList<TonTxRawDTO> {
        val txs = mutableListOf<TonTxRawDTO>()

        block.extra.value.accountBlocks.value.iterator().forEach {
            it.second.value?.transactions?.iterator()?.forEach {
                it.second.value?.value?.let { tx ->
                    txs.add(TonTxRawDTO(tx, block.info.value.seqNo, blockWc))
                }
            }
        }
        block.extra.value.custom.value?.value?.shardHashes?.iterator()?.forEach {
            val workchain = BigInt(it.first.toByteArray()).toInt()
            it.second.nodes().toList().forEach {
                val shardBlock = getBlockId(workchain, it as ShardDescrNew)
                liteClient.getBlock(shardBlock)?.extra?.value?.accountBlocks?.value?.iterator()?.forEach {
                    it.second.value?.transactions?.iterator()?.forEach {
                        it.second.value?.value?.let { tx ->
                            txs.add(TonTxRawDTO(tx, shardBlock.seqno, workchain))
                        }
                    }
                }
            }
        }
        println("block $blockWc:${block.info.value.seqNo} found ${txs.size} txs inside")
        return txs
    }
}

