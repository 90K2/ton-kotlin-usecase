package org.ton.tonkotlinusecase.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.ton.api.liteclient.config.LiteClientConfigGlobal
import org.ton.api.liteserver.LiteServerDesc
import org.ton.api.pub.PublicKeyEd25519
import org.ton.api.validator.config.ValidatorConfigGlobal
import org.ton.crypto.base64
import org.ton.lite.client.LiteClient
import org.ton.tonkotlinusecase.contracts.LiteContract
import org.ton.tonkotlinusecase.ipv4IntToStr
import org.ton.tonkotlinusecase.utcTsNow


@Configuration
class TonBeansConfig {

    @Bean("tonlibra-config-reader")
    fun tonConfig() = TonChainConfigReader()

//    @Bean
    fun liteClient(tonChainConfigReader: TonChainConfigReader): LiteClient {
        return LiteClient(
            liteClientConfigGlobal = LiteClientConfigGlobal(
                liteServers = tonChainConfigReader.load().liteservers.map {
                    LiteServerDesc(id = PublicKeyEd25519(base64(it.id.key)), ip = it.ip, port = it.port)
                }
            ),
            coroutineContext = Dispatchers.Default
        )
    }

    @Bean
    fun liteClientWithCheck(tonChainConfigReader: TonChainConfigReader): LiteClient {
        val configList = tonChainConfigReader.load().liteservers
        val nearestNodesList = mutableListOf<TonChainConfigReader.TonNetConfig.LiteServerParams>()

        configList.forEach {
            val liteClient = LiteClient(
                liteClientConfigGlobal = LiteClientConfigGlobal(
                    liteServers = listOf(
                        LiteServerDesc(id = PublicKeyEd25519(base64(it.id.key)), ip = it.ip, port = it.port)
                    ),
                    validator = ValidatorConfigGlobal()
                ),
                coroutineContext = Dispatchers.Default
            )

            runBlocking {
                try {
                    val lastBlockId = liteClient.getLastBlockId()
                    val start = utcTsNow()

                    liteClient.getBlock(lastBlockId)

                    val delay = utcTsNow().time - start.time
                    println("NODECHECK ${ipv4IntToStr(it.ip)} getLastBlock took $delay ms")
                    if (delay < 350)
                        nearestNodesList.add(it)
                } catch (ex: Exception) {
                    println("NODECHECK ${ipv4IntToStr(it.ip)} FAIL")
                }
            }
        }
        println("NODECHECK: suggested to use ${nearestNodesList.size}/${configList.size} liteservers")

        return LiteClient(
            liteClientConfigGlobal =  LiteClientConfigGlobal(
                liteServers = (nearestNodesList.takeIf { it.isNotEmpty() } ?: configList ).map {
                    LiteServerDesc(id = PublicKeyEd25519(base64(it.id.key)), ip = it.ip, port = it.port)
                },
                validator = ValidatorConfigGlobal()
            ),
            coroutineContext = Dispatchers.Default
        )
    }

    @Bean
    fun liteContract(): LiteContract = LiteContract(liteClient(tonConfig()))
}
