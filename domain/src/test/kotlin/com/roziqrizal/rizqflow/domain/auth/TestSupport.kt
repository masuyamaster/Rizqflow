package com.roziqrizal.rizqflow.domain.auth

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Menjalankan fungsi suspend tanpa pustaka coroutine. Hanya untuk penyimpanan tiruan yang tidak
 * pernah benar-benar menunda; bila menunda, tes gagal dengan pesan jelas.
 */
internal fun <T> runSuspend(block: suspend () -> T): T {
    var result: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
    return checkNotNull(result) { "Fungsi suspend menunda; pakai runBlocking" }.getOrThrow()
}
