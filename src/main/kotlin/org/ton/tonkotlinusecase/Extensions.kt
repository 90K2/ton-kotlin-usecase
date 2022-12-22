package org.ton.tonkotlinusecase

import org.ton.bitstring.BitString
import org.ton.block.*
import org.ton.cell.CellBuilder
import org.ton.cell.CellSlice
import org.ton.tlb.storeTlb
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneOffset

fun CellSlice.loadRemainingBits(): BitString {
    return BitString((this.bitsPosition until this.bits.size).map { this.loadBit() })
}

fun CellSlice.loadRemainingBitsAll(): BitString {
    var r = BitString((this.bitsPosition until this.bits.size).map { this.loadBit() })
    if (this.refs.isNotEmpty()) {
        r += this.refs.first().beginParse().loadRemainingBitsAll()
    }

    return r
}

fun MsgAddress.toAddrString() = (this as AddrStd).toString(true)

fun AccountState?.getState(): StateInit? {
    return when (this) {
        is AccountActive -> this.init
        else -> null
    }
}

fun BitString.clone() = BitString.of(this.toByteArray(), this.size)

fun AddrStd.toSlice() = CellBuilder.createCell {
    storeTlb(MsgAddress, this@toSlice)
}.beginParse()

fun utcNow(): LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)

fun utcLongNow() = utcNow().toEpochSecond(ZoneOffset.UTC)

const val NANOCOIN: Long = 1_000_000_000

fun Double.toNano(): Long = (this * NANOCOIN).toLong()

fun Int.toNano(): Long = (this * NANOCOIN).toLong()

fun Long.fromNano() = BigDecimal(this)
    .divide(BigDecimal(NANOCOIN))

fun BigDecimal.toNano(): Long = this.toDouble().toNano()
