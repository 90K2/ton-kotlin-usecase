package org.ton.tonkotlinusecase

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TonKotlinUsecaseApplication

fun main(args: Array<String>) {
	runApplication<TonKotlinUsecaseApplication>(*args)
}
