package org.ton.tonkotlinusecase.contracts.nft

import org.ton.block.AddrStd
import org.ton.block.MsgAddress
import org.ton.boc.BagOfCells
import org.ton.cell.Cell
import org.ton.cell.CellBuilder
import org.ton.crypto.hex
import org.ton.lite.client.LiteClient
import org.ton.tlb.storeTlb

/**
 * `explicitAddress` - since idk actual dataInit state of any collectable item contract
 *  I cannot relay on calculated in code address. So if it is some third-party NFT it will be better
 *  to use explicit address assign
 */
class NftItemCollectable(
    liteClient: LiteClient,
    ownerAddress: AddrStd,
    override val index: Int,
    override val collectionAddress: AddrStd,
    metadataUrl: String?,
    override val commonMetadataUrl: String,
    override val explicitAddress: AddrStd? = null
) : AbstractNftContract(
    liteClient = liteClient,
    ownerAddress = ownerAddress,
    metadataUrl = metadataUrl ?: "",
    explicitAddress = explicitAddress
) {
    override val address = explicitAddress ?: this.address()

    override val name: String = "Collectable NFT"

    override val code: Cell = CODE

    override fun createDataInit() = CellBuilder.createCell {
        storeUInt(index, 64)
        storeTlb(MsgAddress, collectionAddress)
    }

    // https://github.com/ton-blockchain/token-contract/blob/7d20957ea8d73a6af6e37a7b206db982faa346a3/nft/nft-item.fc
    companion object {
        val CODE: Cell =
            BagOfCells(hex("B5EE9C7241020D010001D6000114FF00F4A413F4BCF2C80B0102016202030202CE04050009A11F9FE00502012006070201200B0C02E30C8871C02497C0F83434C0C05C6C2497C0F83E903E900C7E800C5C75C87E800C7E800C1CEA6D003C00812CE3850C1B088D148CB1C17CB865407E90350C0408FC00F801B4C7F4CFE08417F30F45148C2EA3A24C840DD78C9004F6CF380C0D0D0D4D60840BF2C9A884AEB8C097C12103FCBC20080900113E910C1C2EBCB8536001F65135C705F2E191FA4021F001FA40D20031FA00820AFAF0801BA121945315A0A1DE22D70B01C300209206A19136E220C2FFF2E192218E3E821005138D91C85009CF16500BCF16712449145446A0708010C8CB055007CF165005FA0215CB6A12CB1FCB3F226EB39458CF17019132E201C901FB00104794102A375BE20A00727082108B77173505C8CBFF5004CF1610248040708010C8CB055007CF165005FA0215CB6A12CB1FCB3F226EB39458CF17019132E201C901FB000082028E3526F0018210D53276DB103744006D71708010C8CB055007CF165005FA0215CB6A12CB1FCB3F226EB39458CF17019132E201C901FB0093303234E25502F003003B3B513434CFFE900835D27080269FC07E90350C04090408F80C1C165B5B60001D00F232CFD633C58073C5B3327B552009392989"))
                .first()
    }
}
