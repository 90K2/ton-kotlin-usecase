package org.ton.tonkotlinusecase.client

import org.springframework.stereotype.Component
import org.ton.api.exception.TvmException
import org.ton.api.tonnode.TonNodeBlockId
import org.ton.api.tonnode.TonNodeBlockIdExt
import org.ton.bigint.BigInt
import org.ton.block.AccountInfo
import org.ton.block.AddrStd
import org.ton.block.Block
import org.ton.block.ShardDescr
import org.ton.lite.api.liteserver.LiteServerAccountId
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
    suspend fun getAccount(address: String): AccountInfo? {
        return liteClient.getAccount(address)
    }
    suspend fun getAccount(address: AddrStd): AccountInfo? {
        return liteClient.getAccount(LiteServerAccountId(address))
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

    private fun getBlockId(workchain: Int, descr: ShardDescr) = TonNodeBlockIdExt(
        workchain = workchain,
        shard = descr.next_validator_shard.toLong(),
        seqno = descr.seq_no.toInt(),
        root_hash = descr.root_hash,
        file_hash = descr.file_hash
    )


    suspend fun collectTransactions(block: Block, blockWc: Int, liteClient: LiteClient): MutableList<TonTxRawDTO> {
        val txs = mutableListOf<TonTxRawDTO>()

        block.extra.account_blocks.nodes().forEach {
            it.first.transactions.nodes().forEach {
                txs.add(TonTxRawDTO(it.first, block.info.seq_no.toInt(), blockWc))
            }
        }

        block.extra.custom.value?.shard_hashes?.nodes()?.forEach {
            val workchain = BigInt(it.first.toByteArray()).toInt()
            it.second.nodes().toList().forEach {
                val shardBlock = getBlockId(workchain, it)
                liteClient.getBlock(shardBlock)?.extra?.account_blocks?.nodes()?.forEach {
                    it.first.transactions.nodes().forEach {
                        txs.add(TonTxRawDTO(it.first, shardBlock.seqno, workchain))
                    }
                }
            }
        }
        println("block $blockWc:${block.info.seq_no} found ${txs.size} txs inside")
        return txs
    }
}

