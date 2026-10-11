package dev.joyson.aiworkbench.storage.s3

import dev.joyson.aiworkbench.storage.s3.S3StorageProperties.Companion.orNull
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.http.apache5.Apache5HttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

/**
 * 설정에서 S3 클라이언트를 만든다.
 *
 * 설정 클래스 안의 private 함수가 아니라 여기 있는 이유는 **테스트가 이 함수를 그대로 부르게**
 * 하기 위해서다. 테스트가 자기 손으로 클라이언트를 조립하면 아래 설정들이 검증 밖으로 빠지고,
 * 그것들이 정확히 "안 맞추면 조용히 깨지는" 것들이다.
 */
object S3Clients {

    fun of(properties: S3StorageProperties): S3Client {
        val builder = S3Client.builder()
            .region(Region.of(properties.region))
            .serviceConfiguration(serviceConfiguration(properties))
            // SDK 2.30 부터 모든 PUT 에 CRC32 체크섬을 붙인다. S3 가 아닌 구현은 이것을 거절할 수 있고,
            // 그때 스택트레이스에 "checksum" 이라는 말이 나오지 않아 원인을 찾기 어렵다.
            // 필요할 때만 붙이게 내린다 — 전송 무결성은 HTTPS 가 이미 본다.
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            // JDK 의 HttpURLConnection(url-connection-client)은 PUT 에서 두 가지를 잃는다.
            // `Expect: 100-continue` 에 서버가 거절(404 등)로 답하면 그 XML 본문을 읽지 않고 끊어서 오류 코드가 사라지고,
            // 헤더가 없을 때는 본문을 보내는 중에 서버가 연결을 닫으면 응답 자체가 사라진다.
            // 어느 쪽이든 "버킷 없음" 이 연결 실패로 바뀌어 재시도 대상이 된다. SDK 도 같은 제약을 적어 뒀다.
            .httpClientBuilder(Apache5HttpClient.builder())

        properties.endpoint.orNull()?.let { builder.endpointOverride(URI.create(it)) }

        credentials(properties)?.let { builder.credentialsProvider(it) }

        return builder.build()
    }

    /**
     * 서명 전용 클라이언트.
     *
     * [of] 와 **같은 설정을 따라야 한다.** 리전·자격증명·path-style 중 하나라도 어긋나면
     * 서명은 만들어지는데 보관소가 거부한다 — 그 실패는 발급 시점이 아니라
     * 사용자가 그 주소를 눌렀을 때 403 으로 나타난다.
     *
     * HTTP 클라이언트를 지정하지 않는 이유는 **네트워크를 쓰지 않기 때문**이다. 서명은 로컬 계산이다.
     */
    fun presigner(properties: S3StorageProperties): S3Presigner {
        val builder = S3Presigner.builder()
            .region(Region.of(properties.region))
            .serviceConfiguration(serviceConfiguration(properties))

        properties.endpoint.orNull()?.let { builder.endpointOverride(URI.create(it)) }
        credentials(properties)?.let { builder.credentialsProvider(it) }

        return builder.build()
    }

    private fun serviceConfiguration(properties: S3StorageProperties): S3Configuration =
        S3Configuration.builder()
            .pathStyleAccessEnabled(properties.pathStyleAccess)
            // PUT 은 본문 크기와 무관하게 헤더를 먼저 보내고 서버의 판정(100 또는 거절)을 받은 뒤 본문을 보낸다.
            // SDK 기본은 1 MiB 부터만 그렇게 하고, 그 아래는 거절당하는 중에도 본문을 보내다 응답을 잃을 수 있다.
            // Apache 5 는 이 헤더를 스스로 붙이지 않으므로 여기서 정해야 한다.
            .expectContinueThresholdInBytes(0)
            .build()

    private fun credentials(properties: S3StorageProperties): StaticCredentialsProvider? {
        val accessKey = properties.accessKey.orNull() ?: return null
        val secretKey = properties.secretKey.orNull() ?: return null
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
    }
}
