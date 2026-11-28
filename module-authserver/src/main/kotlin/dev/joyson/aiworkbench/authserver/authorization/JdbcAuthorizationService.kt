package dev.joyson.aiworkbench.authserver.authorization

import dev.joyson.aiworkbench.authserver.AuthorizationServerPaths
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.jdbc.core.RowMapper
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2ErrorCodes
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import java.security.MessageDigest
import java.security.Principal
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * 발급한 인가를 DB 에 둔다. 인가 서버 라이브러리의 JDBC 저장소를 쓰지 않고 직접 구현한 것이다.
 *
 * 라이브러리의 저장소는 토큰 원문 · 접근 토큰 · 인가 요청 전체와 로그인 정보를 JSON 으로 통째로 둔다.
 * 여기서는 다시 대조해야 하는 값만 둔다 — 토큰은 해시, 접근 토큰은 두지 않는다. 열의 이유는 `V22__oauth2_authorization.sql`.
 *
 * ### 같은 인가를 동시에 고치지 못하게 한다 (낙관적 잠금)
 *
 * 라이브러리는 인가를 읽고 → 유효한지 검사하고 → 고쳐서 저장한다. 세 단계 사이를 묶는 것이 없어서, 같은 인가 코드나
 * 같은 리프레시 토큰이 동시에 두 번 오면 둘 다 "유효" 로 읽고 둘 다 토큰을 만든 뒤 마지막 저장이 이긴다.
 * 그래서 읽을 때 `version` 을 인가의 속성으로 실어 두고, 저장은 **그 버전일 때만** 한다. 먼저 저장한 요청이 버전을
 * 올렸으면 늦은 요청의 저장은 0행이 되어 `invalid_grant` 로 거절된다. 판정은 DB 의 한 문장이 하므로
 * 앱 인스턴스가 여럿이어도 같다.
 *
 * ### 원문을 모르는 토큰
 *
 * 저장된 인가를 읽으면 토큰의 원문을 알 수 없다. 라이브러리는 읽은 인가를 고쳐 다시 `save` 하므로,
 * 읽을 때 토큰 값을 [STORED] 로 시작하는 해시로 채워 두고 `save` 에서 그 표시가 있으면 이미 해시인 것으로 본다.
 * 원문은 base64url 이라 `:` 가 없어 섞이지 않는다.
 */
class JdbcAuthorizationService(
    private val jdbc: JdbcOperations,
    private val registeredClients: RegisteredClientRepository,
) : OAuth2AuthorizationService {

    override fun save(authorization: OAuth2Authorization) {
        val version = authorization.getAttribute<Long>(VERSION)
        val request = checkNotNull(authorization.getAttribute<OAuth2AuthorizationRequest>(OAuth2AuthorizationRequest::class.java.name)) {
            "인가 요청이 없는 인가는 저장하지 않는다 — 인가 코드 흐름만 받는다"
        }
        val code = authorization.getToken(OAuth2AuthorizationCode::class.java)
        val refresh = authorization.refreshToken
        // 새 인가는 식별 열까지 넣고, 읽은 인가는 읽은 버전일 때만 고친다.
        val values = arrayOf(
            request.redirectUri,
            request.state,
            authorization.authorizedScopes.joinToString(" "),
            request.additionalParameters[PkceParameterNames.CODE_CHALLENGE],
            request.additionalParameters[PkceParameterNames.CODE_CHALLENGE_METHOD],
            code?.token?.tokenValue?.let(::hashOf),
            code?.token?.issuedAt?.let(::utc),
            code?.token?.expiresAt?.let(::utc),
            code?.isInvalidated ?: false,
            refresh?.token?.tokenValue?.let(::hashOf),
            refresh?.token?.issuedAt?.let(::utc),
            refresh?.token?.expiresAt?.let(::utc),
            refresh?.isInvalidated ?: false,
        )
        val changed = if (version == null) {
            jdbc.update(INSERT, authorization.id, authorization.registeredClientId, authorization.principalName, *values)
        } else {
            jdbc.update(UPDATE, *values, authorization.id, version)
        }
        // 읽은 뒤 다른 요청이 먼저 저장했다. 이 요청이 만든 토큰은 응답으로 나가지 않는다.
        if (changed == 0) {
            throw OAuth2AuthenticationException(OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, "이미 처리된 요청이다", null))
        }
    }

    override fun remove(authorization: OAuth2Authorization) {
        jdbc.update("delete from oauth2_authorization where id = ?", authorization.id)
    }

    override fun findById(id: String): OAuth2Authorization? = find("id", id)

    /**
     * 접근 토큰은 저장하지 않으므로 찾을 수 없다. 접근 토큰을 조회하는 엔드포인트는 막혀 있고,
     * 폐기 요청에 접근 토큰이 오면 라이브러리가 "없음" 으로 받아 성공으로 답한다.
     */
    override fun findByToken(token: String, tokenType: OAuth2TokenType?): OAuth2Authorization? {
        val hash = hashOf(token)
        return when (tokenType?.value) {
            OAuth2ParameterNames.CODE -> find("authorization_code_hash", hash)
            OAuth2TokenType.REFRESH_TOKEN.value -> find("refresh_token_hash", hash)
            null -> find("authorization_code_hash", hash) ?: find("refresh_token_hash", hash)
            else -> null
        }
    }

    private fun find(column: String, value: String): OAuth2Authorization? =
        jdbc.query("select * from oauth2_authorization where $column = ?", ROWS, value).firstNotNullOfOrNull { it }

    private val ROWS = RowMapper<OAuth2Authorization?> { rs, _ -> read(rs) }

    private fun read(rs: ResultSet): OAuth2Authorization? {
        // 등록이 사라진 클라이언트의 인가는 없는 것으로 본다.
        val client = registeredClients.findById(rs.getString("registered_client_id")) ?: return null
        val principalName = rs.getString("principal_name")
        val scopes = rs.getString("scopes").split(" ").filter { it.isNotEmpty() }.toSet()

        val request = OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri(AuthorizationServerPaths.AUTHORIZATION)
            .clientId(client.clientId)
            .redirectUri(rs.getString("redirect_uri"))
            .scopes(scopes)
            .state(rs.getString("state"))
            .additionalParameters {
                it[PkceParameterNames.CODE_CHALLENGE] = rs.getString("code_challenge")
                it[PkceParameterNames.CODE_CHALLENGE_METHOD] = rs.getString("code_challenge_method")
            }
            .build()

        val builder = OAuth2Authorization.withRegisteredClient(client)
            .id(rs.getString("id"))
            .attribute(VERSION, rs.getLong("version"))
            .principalName(principalName)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizedScopes(scopes)
            .attribute(OAuth2AuthorizationRequest::class.java.name, request)
            // 토큰을 만들 때 쓰는 것은 이름(`sub`)뿐이다.
            .attribute(Principal::class.java.name, UsernamePasswordAuthenticationToken.authenticated(principalName, null, emptyList()))

        rs.getString("authorization_code_hash")?.let { hash ->
            val code = OAuth2AuthorizationCode(STORED + hash, instant(rs, "authorization_code_issued_at"), instant(rs, "authorization_code_expires_at"))
            builder.token(code) { it[OAuth2Authorization.Token.INVALIDATED_METADATA_NAME] = rs.getBoolean("authorization_code_invalidated") }
        }
        rs.getString("refresh_token_hash")?.let { hash ->
            val refresh = OAuth2RefreshToken(STORED + hash, instant(rs, "refresh_token_issued_at"), instant(rs, "refresh_token_expires_at"))
            builder.token(refresh) { it[OAuth2Authorization.Token.INVALIDATED_METADATA_NAME] = rs.getBoolean("refresh_token_invalidated") }
        }
        return builder.build()
    }

    private fun instant(rs: ResultSet, column: String): Instant = checkNotNull(rs.getObject(column, OffsetDateTime::class.java)).toInstant()

    private fun utc(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)

    private companion object {
        /** 읽은 인가의 토큰 값 앞에 붙는 표시. 이 표시가 있으면 값은 이미 해시다. */
        const val STORED = "stored:"

        /** 읽은 시점의 행 버전. 인가의 속성으로 실려 `save` 까지 간다. 없으면 새 인가다. */
        const val VERSION = "workbench.authorization.version"

        const val INSERT = """
            insert into oauth2_authorization (
                id, registered_client_id, principal_name,
                redirect_uri, state, scopes, code_challenge, code_challenge_method,
                authorization_code_hash, authorization_code_issued_at, authorization_code_expires_at, authorization_code_invalidated,
                refresh_token_hash, refresh_token_issued_at, refresh_token_expires_at, refresh_token_invalidated
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """

        /** 읽은 버전일 때만 고치고 버전을 올린다. 갱신된 행이 0 이면 다른 요청이 먼저 저장한 것이다. */
        const val UPDATE = """
            update oauth2_authorization set
                redirect_uri = ?, state = ?, scopes = ?, code_challenge = ?, code_challenge_method = ?,
                authorization_code_hash = ?, authorization_code_issued_at = ?, authorization_code_expires_at = ?, authorization_code_invalidated = ?,
                refresh_token_hash = ?, refresh_token_issued_at = ?, refresh_token_expires_at = ?, refresh_token_invalidated = ?,
                version = version + 1
            where id = ? and version = ?
        """

        /** 저장된 값이면 그대로, 원문이면 SHA-256 16진수로. */
        fun hashOf(value: String): String {
            if (value.startsWith(STORED)) return value.removePrefix(STORED)
            return MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
