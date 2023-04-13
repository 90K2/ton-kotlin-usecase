package org.ton.tonkotlinusecase

import org.ton.bitstring.BitString
import org.ton.block.*
import org.ton.cell.CellBuilder
import org.ton.cell.CellSlice
import org.ton.contract.wallet.WalletTransfer
import org.ton.crypto.Ed25519
import org.ton.lite.api.liteserver.LiteServerAccountId
import org.ton.mnemonic.Mnemonic
import org.ton.tlb.storeTlb
import org.ton.tonkotlinusecase.constants.SendMode
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

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
        is AccountActive -> this.value
        else -> null
    }
}

fun LiteServerAccountId(address: AddrStd): LiteServerAccountId = LiteServerAccountId(
    address.workchainId, address.address.toByteArray()
)

fun Mnemonic.toKeyPair(mnemonic: List<String>, password: String = ""): Pair<ByteArray, ByteArray> {
    val sk = toSeed(mnemonic, password)
    return Pair(Ed25519.publicKey(sk), sk)
}

fun List<Pair<String, Long>>.toWalletTransfer() = this.map {
    WalletTransfer {
        destination = AddrStd(it.first)
        coins = Coins.ofNano(it.second)
        bounceable = true
        body = null
        stateInit = null
        sendMode = SendMode.PAY_GAS_SEPARATELY
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

fun ipv4IntToStr(ip: Int): String {
    return String.format(
        Locale.US, "%d.%d.%d.%d",
        ip shr 24 and 0xff,
        ip shr 16 and 0xff,
        ip shr 8 and 0xff,
        ip and 0xff
    )
}

fun utcTsNow() = Timestamp.valueOf(utcNow())
