package org.ton.tonkotlinusecase.contracts

import org.slf4j.LoggerFactory
import org.ton.block.*
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.cell.buildCell
import org.ton.contract.SmartContract
import org.ton.lite.client.LiteClient
import org.ton.tlb.storeTlb

abstract class RootContract(
    override val liteClient: LiteClient
) : SmartContract {

    protected val logger = LoggerFactory.getLogger(this::class.simpleName)

    abstract fun createDataInit(): Cell

    abstract val sourceCode: Cell

    fun createStateInit() = StateInit(
            sourceCode, createDataInit()
    )

    override val address by lazy {
        address()
    }

    fun address(stateInit: StateInit = createStateInit(), workchain: Int = 0) =
        AddrStd(workchain, buildCell { storeTlb(StateInit, stateInit) }.hash())

    fun packStateInit(stateInit: StateInit) = CellBuilder.createCell {
        storeTlb(StateInit.tlbCodec(), stateInit)
    }

}

public data class SmartContractAnswer(
    val success: Boolean,
    val stack: VmStack?,
    val exitCode: Int
)
