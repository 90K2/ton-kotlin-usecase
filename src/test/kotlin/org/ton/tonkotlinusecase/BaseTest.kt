package org.ton.tonkotlinusecase

import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [TonKotlinUsecaseApplication::class]
)
class BaseTest {
}