package org.ton.tonkotlinusecase.constants

object OpCodes {
    const val OP_WITHDRAW = 2222
    const val OP_MARKET_DEPLOY_NFT_SELLER = 11
    const val OP_MARKET_DEPLOY_FT_SELLER = 12

    const val OP_MARKET_CANCEL_NFT_SALE = 31
    const val OP_MARKET_CANCEL_FT_SALE = 32

    const val OP_SELLER_INIT = 1
    const val OP_SELLER_BUY = 2
    const val OP_SELLER_CANCEL = 3

    const val OP_COLLECTION_MINT = 1
    const val OP_COLLECTION_MINT_BATCH = 2

    //    const val OP_CHANGE_OWNER = 3
    const val OP_CHANGE_CONTENT_AND_ROYALTY = 4

    const val OP_NFT_TRANSFER = 0x5fcc3d14
    const val OP_NFT_OWNERSHIP_ASSIGNED = 0x5138d91

    const val OP_SFT_MINTER_MINT = 21
    const val OP_SFT_WALLET_BURN = 0x595f07bc

    // ft tokens transfer triggered by ft wallet
    const val OP_WALLET_TRANSFER = 0xf8a7ea5

    // ft tokens initial transfer triggered by ft minter
    // or between wallets
    const val OP_WALLET_INTERTRANSFER = 0x178d4519

    const val OP_SFT_TRANSFER_NOTIFICATION = 0x7362d09c
}
