package org.ton.tonkotlinusecase.constants

object OpCodes {

    const val OP_COLLECTION_MINT = 1
    const val OP_COLLECTION_MINT_BATCH = 2

//    const val OP_CHANGE_OWNER = 3
    const val OP_CHANGE_CONTENT_AND_ROYALTY = 4

    const val OP_NFT_TRANSFER = 0x5fcc3d14
    const val OP_NFT_OWNERSHIP_ASSIGNED = 0x5138d91

    const val OP_JETTON_MINTER_MINT = 21
    const val OP_JETTON_WALLET_BURN = 0x595f07bc

    const val OP_WALLET_TRANSFER = 0xf8a7ea5

    // initial transfer or between wallets
    const val OP_WALLET_INTERTRANSFER = 0x178d4519
}
