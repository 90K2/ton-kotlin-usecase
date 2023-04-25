package org.ton.tonkotlinusecase.contracts

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.ton.api.tonnode.TonNodeBlockIdExt
import org.ton.block.*
import org.ton.cell.Cell
import org.ton.cell.buildCell
import org.ton.lite.client.LiteClient
import org.ton.tlb.storeTlb
import org.ton.tonkotlinusecase.LiteServerAccountId
import org.ton.tonkotlinusecase.toAddrString

abstract class SmartContract(
    open val liteClient: LiteClient,
    open val workchain: Int
): org.ton.contract.SmartContract<Any> {

    abstract val CODE: Cell

    abstract fun createDataInit(): Cell

    fun createStateInit() = StateInit(
        code = CODE,
        data = createDataInit()
    )

    fun address(stateInit: StateInit = createStateInit(), workchain: Int? = null): AddrStd =
        AddrStd(workchain ?: this.workchain, buildCell { storeTlb(StateInit, stateInit) }.hash())


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

    private val logger = LoggerFactory.getLogger(this::class.simpleName)

}
