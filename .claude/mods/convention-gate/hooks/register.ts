import type { EngineInterface, Register, ToolCallResult } from 'claude-code'
import { changedPaths, check, format, relativePath, type Conventions } from './rules'

const CONVENTIONS_FILE = '.claude/conventions.json'

/** 검사할 파일 하나: 절대 경로, 규약이 말하는 경로, 그 저장소의 규약. */
type Target = { abs: string; path: string; conventions: Conventions }

/** 파일이 그대로인데 같은 위반을 거듭 알리지 않는다. 모듈이 다시 로드되면 비워진다. */
const told = new Set<string>()

/**
 * AI 가 Kotlin 파일을 쓰는 순간 저장소의 `.claude/conventions.json` 을 건다.
 *
 * - Write 로 **새 파일**을 만들 때: 쓰기 전에 검사하고, 어기면 쓰지 않는다(deny).
 *   만드는 순간이 가장 싸다. 그 위에 다른 코드가 쌓이기 전이다.
 * - 그 밖의 변경(Write 로 덮기, Edit, Bash): 바뀐 뒤 검사하고, 어기면 모델에게만 알린다(context).
 *   도구가 아니라 **결과로 바뀐 파일**을 본다. 이름 바꾸기는 대개 Bash(git mv, perl)로 하기 때문이다.
 *
 * 규약 파일이 없는 저장소에서는 아무것도 하지 않는다.
 * 검사기가 예외를 던지면 그 호출은 그냥 통과시킨다. 규약 검사가 고장 났다고 작업을 막지 않는다
 * (`next` 는 다시 불러도 도구를 두 번 돌리지 않는다).
 */
export const register: Register = on => {
  on('tool.call', { tool: 'Write' }, async ($, e, next) => {
    const target = await locate($, e.file_path)
    if (target && !(await $.fs.exists(e.file_path))) {
      const violations = check(target.conventions, { path: target.path, text: e.content })
      if (violations.length > 0) return { deny: format(target.path, violations) }
    }
    const ran = await next(e)
    return target ? remind($, ran, [target]) : ran
  }).catch(($, e, next) => next(e))

  on('tool.call', { tool: 'Edit' }, async ($, e, next) => {
    const ran = await next(e)
    const target = await locate($, e.file_path)
    return target ? remind($, ran, [target]) : ran
  }).catch(($, e, next) => next(e))

  on('tool.call', { tool: 'Bash' }, async ($, e, next) => {
    const ran = await next(e)
    if (ran.deny !== undefined) return ran
    const targets: Target[] = []
    for (const root of await rootsTouchedBy($, e.command)) targets.push(...(await changedFiles($, root)))
    return targets.length > 0 ? remind($, ran, targets) : ran
  }).catch(($, e, next) => next(e))
}

/** 바뀐 파일을 검사해, 처음 보는 위반만 모델에게 알린다. */
async function remind<R extends ToolCallResult>($: EngineInterface, ran: R, targets: Target[]): Promise<R> {
  if (ran.deny !== undefined) return ran
  const notes: string[] = []
  for (const t of targets) {
    if (!(await $.fs.exists(t.abs))) continue
    const text = await $.fs.read(t.abs)
    const fresh = check(t.conventions, { path: t.path, text }).filter(v => {
      const key = [t.abs, v.rule.id, v.detail, text.length].join('\u0000')
      if (told.has(key)) return false
      told.add(key)
      return true
    })
    if (fresh.length > 0) notes.push(format(t.path, fresh))
  }
  return notes.length === 0 ? ran : { ...ran, context: [...(ran.context ?? []), ...notes] }
}

/** 파일이 속한 저장소의 규약을 찾는다. 규약이 없거나 규약이 다루는 소스가 아니면 null. */
async function locate($: EngineInterface, absolutePath: string): Promise<Target | null> {
  const root = await conventionsRoot($, parentOf(absolutePath))
  if (!root) return null
  const conventions = await readConventions($, root)
  const path = relativePath(conventions, root, absolutePath)
  return path ? { abs: absolutePath, path, conventions } : null
}

/** 위로 올라가며 `.claude/conventions.json` 이 있는 폴더를 찾는다. */
async function conventionsRoot($: EngineInterface, from: string): Promise<string | null> {
  for (let dir = from; dir.length > 0; dir = parentOf(dir)) {
    if (await $.fs.exists(`${dir}/${CONVENTIONS_FILE}`)) return dir
    if (dir === '/') break
  }
  return null
}

async function readConventions($: EngineInterface, root: string): Promise<Conventions> {
  return JSON.parse(await $.fs.read(`${root}/${CONVENTIONS_FILE}`)) as Conventions
}

/**
 * 명령이 건드렸을 수 있는 저장소들: 세션의 작업 폴더, 그리고 명령에 나온 절대 경로.
 * `cd /다른/워크트리 && …` 처럼 세션 밖 저장소를 바꾸는 명령도 놓치지 않으려고 경로를 함께 본다.
 */
async function rootsTouchedBy($: EngineInterface, command: string): Promise<string[]> {
  const candidates = new Set<string>()
  const top = await $.process.run(['git', 'rev-parse', '--show-toplevel'])
  if (top.exitCode === 0) candidates.add(top.stdout.trim())
  for (const m of command.matchAll(/(?:^|[\s'"=])(\/[^\s'";|&<>()]+)/g)) {
    if (m[1]) candidates.add(m[1])
    if (candidates.size >= 12) break
  }
  const roots = new Set<string>()
  for (const path of candidates) {
    const root = await conventionsRoot($, path)
    if (root) roots.add(root)
  }
  return [...roots]
}

/** 커밋되지 않은 변경 중 규약이 다루는 Kotlin 파일. 지워진 파일은 뺀다. */
async function changedFiles($: EngineInterface, root: string): Promise<Target[]> {
  const conventions = await readConventions($, root)
  const status = await $.process.run(['git', '-C', root, 'status', '--porcelain', '-uall', '--', conventions.sourceRoot])
  if (status.exitCode !== 0) return []
  return changedPaths(status.stdout)
    .map(file => `${root}/${file}`)
    .flatMap(abs => {
      const path = relativePath(conventions, root, abs)
      return path ? [{ abs, path, conventions }] : []
    })
}

function parentOf(path: string): string {
  const i = path.replace(/\/+$/, '').lastIndexOf('/')
  return i <= 0 ? '/' : path.slice(0, i)
}
