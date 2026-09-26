package dev.joyson.aiworkbench.storage.config

import java.time.Duration

/**
 * 보관소가 기동에 남길 한 줄을 조립한다.
 *
 * **무엇을 쓰는지는 이 함수가 모른다.** 구현별 설명은 각 팩토리가 만들고 여기서는 붙이기만 한다 —
 * 그래야 보관소를 더할 때 고칠 자리가 팩토리 하나로 끝난다.
 *
 * 빈이 아니라 순수 함수인 이유는, 비밀이 새지 않는지를 확인하려고 스프링을 띄울 일이 아니어서다.
 */
internal fun describeStorage(
    provider: String,
    detail: String,
    presigning: Boolean,
    presignedUrlTtl: Duration,
): String {
    val signing =
        if (presigning) "서명주소=발급함(${presignedUrlTtl.toMinutes()}분)"
        else "서명주소=발급 못 함 → 앱이 직접 내보낸다"

    return "보관소=$provider $detail $signing"
}
