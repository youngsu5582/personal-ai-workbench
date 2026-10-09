import type { On } from 'claude-code'
import { expect, test } from 'claude-code/testing'
import { BASE_DIR, CONVENTIONS } from './conventions.fixture'

/*
 * 플러그인 훅이 실제 도구 호출에서 막고 알리는지 본다.
 *
 * 테스트의 `on` 훅은 엔진 자리다. 플러그인이 부르는 `$.fs`·`$.process` 도 엔진이 답하는 이벤트라,
 * 여기서 메모리 위의 저장소로 답한다(`{ value }` 로 감싼다). 디스크를 건드리지 않으니 어디서 돌려도 같다.
 */

const ROOT = '/repo'
const APP = `${ROOT}/${BASE_DIR}/generation/application`

type Host = {
  files: Record<string, string>
  /** 엔진이 실제로 쓴 파일. 막힌 쓰기는 여기 오지 않는다. */
  written: string[]
  /** `git status --porcelain` 이 답할 내용. */
  status: string
}

/** 규약 파일이 있는 메모리 저장소를 엔진 자리에 깐다. */
function virtualRepo(on: On, files: Record<string, string> = {}, withConventions = true): Host {
  const host: Host = { files: { ...files }, written: [], status: '' }
  if (withConventions) host.files[`${ROOT}/.claude/conventions.json`] = JSON.stringify(CONVENTIONS)

  on('fs.exists', async (_$, e) => ({ value: e.path in host.files }))
  on('fs.read', async (_$, e) => {
    const text = host.files[e.path]
    if (text === undefined) throw new Error(`없는 파일이다: ${e.path}`)
    return { value: text }
  })
  on('process.run', async (_$, e) => {
    const isStatus = e.argv[0] === 'git' && e.argv.includes('status')
    const isTop = e.argv[0] === 'git' && e.argv.includes('--show-toplevel')
    return {
      value: {
        exitCode: isStatus || isTop ? 0 : 1,
        stdout: isStatus ? host.status : isTop ? `${ROOT}\n` : '',
        stderr: '',
        isStdoutTruncated: false,
        isStderrTruncated: false,
      },
    }
  })
  on('tool.call', { tool: 'Write' }, async (_$, e) => {
    host.files[e.file_path] = e.content
    host.written.push(e.file_path)
    return { result: {} }
  })
  on('tool.call', { tool: 'Bash' }, async () => ({ result: { stdout: '', stderr: '' } }))
  return host
}

test('규약을 어기는 새 파일은 쓰기 전에 막는다', async ($, on) => {
  const host = virtualRepo(on)

  const ran = await $.tool.call({ tool: 'Write', file_path: `${APP}/UsageReader.kt`, content: 'class UsageReader\n' })

  expect(host.written).toEqual([])
  const said = JSON.stringify(ran)
  expect(said).toContain('application-roles')
  expect(said).toContain('banned-suffixes')
  expect(said).toContain('허용되는 끝말: Service(진입점)')
  expect(said).toContain('사용자에게 묻는다')
})

test('규약을 지키는 새 파일은 그대로 쓴다', async ($, on) => {
  const host = virtualRepo(on)

  await $.tool.call({ tool: 'Write', file_path: `${APP}/UsageService.kt`, content: 'class UsageService\n' })

  expect(host.written).toEqual([`${APP}/UsageService.kt`])
})

test('있던 파일을 Write 로 덮으면 막지 않고 알린다', async ($, on) => {
  const host = virtualRepo(on, { [`${APP}/TaskWorker.kt`]: 'class TaskWorker\n' })
  const broken = 'import dev.joyson.aiworkbench.generation.infrastructure.JpaGenerationJobRepository\n\nclass TaskWorker\n'

  const ran = await $.tool.call({ tool: 'Write', file_path: `${APP}/TaskWorker.kt`, content: broken })

  expect(host.written).toEqual([`${APP}/TaskWorker.kt`])
  expect(JSON.stringify(ran)).toContain('application-no-infrastructure')
})

test('규약 파일이 없는 저장소는 건드리지 않는다', async ($, on) => {
  const host = virtualRepo(on, {}, false)

  const ran = await $.tool.call({ tool: 'Write', file_path: `${APP}/UsageReader.kt`, content: 'class UsageReader\n' })

  expect(host.written).toEqual([`${APP}/UsageReader.kt`])
  expect(JSON.stringify(ran)).not.toContain('convention-gate')
})

test('Edit 뒤의 위반은 모델에게 알리고, 파일이 그대로면 거듭 알리지 않는다', async ($, on) => {
  const file = `${APP}/GenerationJobService.kt`
  const host = virtualRepo(on, { [file]: 'class GenerationJobService\n' })
  const broken = 'import dev.joyson.aiworkbench.generation.infrastructure.JpaGenerationJobRepository\n\nclass GenerationJobService\n'
  on('tool.call', { tool: 'Edit' }, async (_$, e) => {
    host.files[e.file_path] = broken
    return { result: {} }
  })

  const first = await $.tool.call({ tool: 'Edit', file_path: file, old_string: 'a', new_string: 'b' })
  const again = await $.tool.call({ tool: 'Edit', file_path: file, old_string: 'a', new_string: 'b' })

  expect(JSON.stringify(first)).toContain('application-no-infrastructure')
  expect(JSON.stringify(again)).not.toContain('application-no-infrastructure')
})

test('Bash 로 바뀐 파일도 결과를 보고 알린다', async ($, on) => {
  const host = virtualRepo(on, { [`${APP}/UsageQuery.kt`]: 'class UsageQuery\n' })
  host.status = `?? ${BASE_DIR}/generation/application/UsageQuery.kt\n`

  const ran = await $.tool.call({ tool: 'Bash', command: 'git mv UsageReader.kt UsageQuery.kt' })

  const said = JSON.stringify(ran)
  expect(said).toContain('application-roles')
  expect(said).toContain('UsageQuery')
})

test('Bash 가 규약 밖 파일만 바꿨으면 조용하다', async ($, on) => {
  const host = virtualRepo(on)
  host.status = ' M README.md\n'

  const ran = await $.tool.call({ tool: 'Bash', command: 'echo hi >> README.md' })

  expect(JSON.stringify(ran)).not.toContain('convention-gate')
})
