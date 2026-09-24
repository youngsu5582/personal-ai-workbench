package dev.joyson.aiworkbench.storage.s3

import dev.joyson.aiworkbench.storage.s3.S3StorageProperties.Companion.orNull
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import java.net.URI

/**
 * 설정에서 S3 클라이언트를 만든다.
 *
 * 설정 클래스 안의 private 함수가 아니라 여기 있는 이유는 **테스트가 이 함수를 그대로 부르게**
 * 하기 위해서다. 테스트가 자기 손으로 클라이언트를 조립하면 아래 두 설정이 검증 밖으로 빠지고,
 * 그 둘이 정확히 "안 맞추면 조용히 깨지는" 것들이다.
 */
object S3Clients {

    fun of(properties: S3StorageProperties): S3Client {
        val builder = S3Client.builder()
            .region(Region.of(properties.region))
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(properties.pathStyleAccess)
                    .build(),
            )
            // SDK 2.30 부터 모든 PUT 에 CRC32 체크섬을 붙인다. S3 가 아닌 구현은 이것을 거절할 수 있고,
            // 그때 스택트레이스에 "checksum" 이라는 말이 나오지 않아 원인을 찾기 어렵다.
            // 필요할 때만 붙이게 내린다 — 전송 무결성은 HTTPS 가 이미 본다.
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            // s3 모듈은 HTTP 구현체를 전이 의존으로 갖지 않는다. 지정하지 않으면 컴파일은 되고
            // 클라이언트를 만드는 순간 터진다.
            .httpClient(UrlConnectionHttpClient.create())

        properties.endpoint.orNull()?.let { builder.endpointOverride(URI.create(it)) }

        val accessKey = properties.accessKey.orNull()
        val secretKey = properties.secretKey.orNull()
        if (accessKey != null && secretKey != null) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)),
            )
        }

        return builder.build()
    }
}
