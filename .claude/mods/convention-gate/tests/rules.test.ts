import { expect, test } from 'claude-code/testing'
import { changedPaths, check, relativePath, type Conventions } from '../hooks/rules'
import { CONVENTIONS } from './conventions.fixture'

const ids = (path: string, text: string) => check(CONVENTIONS, { path, text }).map(v => v.rule.id)

test('규약을 지킨 진입점은 통과한다', () => {
  const text = [
    'package dev.joyson.aiworkbench.generation.application',
    '',
    'import dev.joyson.aiworkbench.generation.domain.GenerationJobRepository',
    '',
    '@Service',
    'class GenerationJobService(private val repository: GenerationJobRepository) {',
    '}',
  ].join('\n')
  expect(ids('generation/application/GenerationJobService.kt', text)).toEqual([])
})

test('목록에 없는 역할의 파일 이름을 잡는다', () => {
  expect(ids('generation/application/FooManager.kt', 'class FooManager')).toEqual(['application-roles'])
})

test('Default 로 시작하는 공개 포트 구현은 통과한다', () => {
  expect(ids('user/application/DefaultUserRegistry.kt', 'class DefaultUserRegistry : UserRegistry')).toEqual([])
})

test('Reader 는 역할 규칙과 금지 끝말 규칙에 함께 걸린다', () => {
  expect(ids('usage/application/UsageReader.kt', 'class UsageReader')).toEqual(['application-roles', 'banned-suffixes'])
})

test('예외로 적힌 파일은 역할 규칙을 건너뛴다', () => {
  expect(ids('auth/application/WorkbenchOidcUser.kt', 'class WorkbenchOidcUser')).toEqual([])
})

test('application 이 infrastructure 를 import 하면 잡는다', () => {
  const text = 'import dev.joyson.aiworkbench.generation.infrastructure.ProviderRequestMapper\n\nclass TaskWorker'
  expect(ids('generation/application/TaskWorker.kt', text)).toContain('application-no-infrastructure')
})

test('domain 이 application 을 import 하면 잡는다', () => {
  const text = 'import dev.joyson.aiworkbench.generation.application.GenerationCommand\n\nclass GenerationJob'
  expect(ids('generation/domain/GenerationJob.kt', text)).toEqual(['domain-depends-on-nothing'])
})

test('domain 의 Repository 가 interface 가 아니면 잡는다', () => {
  expect(ids('generation/domain/GenerationJobRepository.kt', 'class GenerationJobRepository')).toEqual(['ports-are-interfaces'])
  expect(ids('generation/domain/GenerationJobRepository.kt', 'interface GenerationJobRepository {\n}')).toEqual([])
  expect(ids('generation/domain/GeneratedFileFinder.kt', 'fun interface GeneratedFileFinder {\n}')).toEqual([])
})

test('포트 구현은 기술이 앞에 와야 통과한다', () => {
  const impl = (name: string) =>
    [
      'import dev.joyson.aiworkbench.generation.domain.GenerationJob',
      'import dev.joyson.aiworkbench.generation.domain.GenerationJobRepository',
      'import org.springframework.data.jpa.repository.JpaRepository',
      '',
      `interface ${name} : JpaRepository<GenerationJob, Long>, GenerationJobRepository {`,
      '}',
    ].join('\n')
  expect(ids('generation/infrastructure/JpaGenerationJobRepository.kt', impl('JpaGenerationJobRepository'))).toEqual([])
  expect(ids('generation/infrastructure/GenerationJobJpaRepository.kt', impl('GenerationJobJpaRepository'))).toEqual(['port-impl-name'])
})

test('주 생성자가 여러 줄이어도 포트 구현을 읽는다', () => {
  const text = [
    'import dev.joyson.aiworkbench.generation.domain.GeneratedFileFinder',
    '',
    '@Repository',
    'class GeneratedFileFinderAdapter(',
    '    private val dsl: DSLContext,',
    ') : GeneratedFileFinder {',
    '}',
  ].join('\n')
  expect(ids('generation/infrastructure/GeneratedFileFinderAdapter.kt', text)).toEqual(['port-impl-name'])
})

test('컨트롤러는 Service 만 받는다', () => {
  const controller = (type: string) =>
    [
      `import dev.joyson.aiworkbench.generation.application.${type}`,
      '',
      '@RestController',
      'class GenerationJobController(',
      `    private val dependency: ${type},`,
      ') {',
      '}',
    ].join('\n')
  expect(ids('generation/api/GenerationJobController.kt', controller('GenerationJobService'))).toEqual([])
  expect(ids('generation/api/GenerationJobController.kt', controller('GenerationJobWriter'))).toEqual(['controllers-call-services'])
})

test('규약이 다루는 Kotlin 소스만 대상이다', () => {
  const root = '/repo'
  expect(relativePath(CONVENTIONS, root, '/repo/src/main/kotlin/dev/joyson/aiworkbench/generation/application/X.kt')).toBe(
    'generation/application/X.kt',
  )
  expect(relativePath(CONVENTIONS, root, '/repo/src/test/kotlin/dev/joyson/aiworkbench/generation/XTest.kt')).toBeNull()
  expect(relativePath(CONVENTIONS, root, '/repo/src/main/kotlin/dev/joyson/aiworkbench/generation/application/notes.md')).toBeNull()
})

test('모르는 검사 종류는 조용히 넘어가지 않는다', () => {
  const unknown = {
    ...CONVENTIONS,
    rules: [{ id: 'future', rule: '아직 없는 검사', why: '', check: { kind: 'someday' } }],
  } as unknown as Conventions
  const violations = check(unknown, { path: 'generation/application/GenerationJobService.kt', text: '' })
  expect(violations).toHaveLength(1)
  expect(violations[0]?.detail).toContain('모르는 검사 종류')
})

test('git status 에서 지금 있는 바뀐 파일만 뽑는다', () => {
  const porcelain = [
    ' M src/main/kotlin/a/Kept.kt',
    '?? src/main/kotlin/a/New.kt',
    'A  src/main/kotlin/a/Added.kt',
    ' D src/main/kotlin/a/Gone.kt',
    'R  src/main/kotlin/a/Old.kt -> src/main/kotlin/a/Renamed.kt',
    '?? "src/main/kotlin/a/With Space.kt"',
    '',
  ].join('\n')
  expect(changedPaths(porcelain)).toEqual([
    'src/main/kotlin/a/Kept.kt',
    'src/main/kotlin/a/New.kt',
    'src/main/kotlin/a/Added.kt',
    'src/main/kotlin/a/Renamed.kt',
    'src/main/kotlin/a/With Space.kt',
  ])
})
