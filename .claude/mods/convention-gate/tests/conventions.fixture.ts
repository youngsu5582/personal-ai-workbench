import type { Conventions } from '../hooks/rules'

/**
 * 테스트용 규약. 저장소의 `.claude/conventions.json` 과 같은 모양이고, 검사 종류마다 규칙 하나씩 둔다.
 * 저장소 파일을 읽지 않는 이유: 테스트가 어느 폴더에서 돌든 같은 결과를 내야 한다.
 */
export const CONVENTIONS: Conventions = {
  basePackage: 'dev.joyson.aiworkbench',
  sourceRoot: 'src/main/kotlin',
  layers: ['api', 'application', 'domain', 'infrastructure', 'config'],
  rules: [
    {
      id: 'application-no-infrastructure',
      rule: 'application 은 infrastructure 를 import 하지 않는다',
      why: '구현 기술이 바뀌어도 유스케이스는 그대로여야 한다',
      check: { kind: 'no-import', from: 'application', to: ['infrastructure'] },
    },
    {
      id: 'domain-depends-on-nothing',
      rule: 'domain 은 바깥 층을 import 하지 않는다',
      why: '의존은 바깥에서 도메인으로 향한다',
      check: { kind: 'no-import', from: 'domain', to: ['application', 'api', 'infrastructure'] },
    },
    {
      id: 'application-roles',
      rule: 'application 의 파일 이름은 정해진 역할로 끝난다',
      why: '이름만 보고 진입점인지 부품인지 알게 한다',
      check: {
        kind: 'file-name',
        in: 'application',
        endsWith: { Service: '진입점', Writer: '부품' },
        startsWith: { Default: '공개 포트의 기본 구현' },
        except: { 'auth/application/WorkbenchOidcUser.kt': '프레임워크 역할' },
      },
      ok: ['GenerationJobService'],
    },
    {
      id: 'banned-suffixes',
      rule: '다음 끝말로 타입 이름을 짓지 않는다',
      why: '실제와 다른 층을 가리킨다',
      check: { kind: 'banned-suffix', suffixes: { Reader: '데이터 접근으로 읽힌다', Impl: '{기술}{포트} 가 대신한다' } },
    },
    {
      id: 'ports-are-interfaces',
      rule: 'domain 의 Repository 는 interface 다',
      why: '도메인은 계약만 갖는다',
      check: { kind: 'interface-only', in: 'domain', endsWith: ['Repository', 'Finder'] },
    },
    {
      id: 'port-impl-name',
      rule: '포트 구현은 {기술}{포트}',
      why: '이름만 보고 무엇을 어떤 기술로 구현했는지 안다',
      check: {
        kind: 'port-impl-name',
        portsIn: 'domain',
        portEndsWith: ['Repository', 'Finder'],
        implIn: 'infrastructure',
        prefixes: { Jpa: 'Spring Data JPA', Jooq: 'jOOQ' },
      },
    },
    {
      id: 'controllers-call-services',
      rule: '컨트롤러가 받는 application 타입은 Service 뿐이다',
      why: '층이 이름에 드러나게 한다',
      check: { kind: 'controller-deps', in: 'api', controllerEndsWith: 'Controller', typesFrom: 'application', allowedEndsWith: ['Service'] },
    },
  ],
}

export const BASE_DIR = 'src/main/kotlin/dev/joyson/aiworkbench'
