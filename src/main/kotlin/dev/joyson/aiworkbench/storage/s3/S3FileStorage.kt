package dev.joyson.aiworkbench.storage.s3

import dev.joyson.aiworkbench.storage.FileStorage
import dev.joyson.aiworkbench.storage.FileStorageException
import org.slf4j.LoggerFactory
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest

/**
 * S3 호환 보관소에 객체로 둔다.
 *
 * 키를 경로로 바꾸지 않는다 — S3 에 디렉토리는 없고 키가 곧 이름이다. 그래서
 * `LocalFileStorage` 에 있던 `..` 탈출 검사가 여기엔 없다. `a/../b` 라는 키는 탈출이 아니라
 * 그냥 그 이름의 객체다.
 */
class S3FileStorage(
    private val client: S3Client,
    private val bucket: String,
) : FileStorage {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun put(key: String, content: ByteArray, contentType: String) {
        try {
            client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build(),
                RequestBody.fromBytes(content),
            )
        } catch (e: SdkException) {
            throw translate("객체를 쓰지 못했다: $key", e)
        }
        log.debug("결과물을 저장했다. bucket={} key={} bytes={} type={}", bucket, key, content.size, contentType)
    }

    override fun read(key: String): ByteArray? =
        try {
            client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(key).build(),
            ).asByteArray()
        } catch (e: NoSuchKeyException) {
            // 없는 것은 실패가 아니다. 포트가 그렇게 약속했다.
            null
        } catch (e: SdkException) {
            throw translate("객체를 읽지 못했다: $key", e)
        }

    /**
     * SDK 예외를 포트의 예외로 바꾼다.
     *
     * **`LocalFileStorage` 와 기본값이 반대다.** 거기서는 모르는 예외를 `retryable = true` 로 두지만
     * 여기서는 서버가 준 응답을 보고 가른다 — SDK 가 이미 분류해 주기 때문이다.
     * 4xx 는 요청이 잘못된 것이라 다시 보내도 같고(버킷 없음·권한 없음), 5xx 와 스로틀링은
     * 상대 사정이라 다시 해볼 만하다. 연결 자체가 안 된 경우(`SdkClientException`)도 마찬가지다.
     */
    private fun translate(message: String, e: SdkException): FileStorageException {
        val retryable = when (e) {
            is AwsServiceException -> e.isThrottlingException || e.statusCode() >= 500
            else -> true
        }
        return FileStorageException(retryable = retryable, message = message, cause = e)
    }
}
