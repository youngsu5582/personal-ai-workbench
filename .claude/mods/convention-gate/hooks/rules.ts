/**
 * `.claude/conventions.json` 의 규칙을 Kotlin 소스 **텍스트**에 건다.
 *
 * 저장소의 ArchitectureTest 가 같은 파일을 컴파일된 클래스에 건다. 여기는 파일을 쓰는 순간에
 * 컴파일 없이 답해야 하므로 텍스트로 읽는다 — import 줄, 선언 줄, 상위 타입 목록, 주 생성자.
 * 규칙의 문장·이유·예는 그 파일에 있고, 여기는 검사 종류(`check.kind`)마다 방법만 안다.
 */

export type Conventions = {
  basePackage: string
  sourceRoot: string
  layers: string[]
  rules: Rule[]
}

export type Rule = {
  id: string
  rule: string
  why: string
  ok?: string[]
  ng?: string[]
  check: Check
}

export type Check =
  | { kind: 'no-import'; from: string; to: string[] }
  | {
      kind: 'file-name'
      in: string
      endsWith: Record<string, string>
      startsWith?: Record<string, string>
      except?: Record<string, string>
    }
  | { kind: 'banned-suffix'; suffixes: Record<string, string> }
  | { kind: 'interface-only'; in: string; endsWith: string[] }
  | {
      kind: 'port-impl-name'
      portsIn: string
      portEndsWith: string[]
      implIn: string
      prefixes: Record<string, string>
    }
  | {
      kind: 'controller-deps'
      in: string
      controllerEndsWith: string
      typesFrom: string
      allowedEndsWith: string[]
    }

/** `path` 는 기준 패키지 폴더에서 본 경로다: `generation/application/GenerationJobService.kt`. */
export type SourceFile = { path: string; text: string }

export type Violation = { rule: Rule; detail: string }

type Declaration = { kind: 'class' | 'interface' | 'object'; name: string; start: number }
type Import = { fqn: string; simpleName: string; layer: string | null }

/** 저장소 루트와 파일의 절대 경로로 규칙이 말하는 경로를 만든다. 대상이 아니면 null. */
export function relativePath(conv: Conventions, root: string, absolutePath: string): string | null {
  const dir = `${trimSlash(root)}/${trimSlash(conv.sourceRoot)}/${conv.basePackage.replaceAll('.', '/')}/`
  if (!absolutePath.startsWith(dir) || !absolutePath.endsWith('.kt')) return null
  return absolutePath.slice(dir.length)
}

export function check(conv: Conventions, file: SourceFile): Violation[] {
  const ctx = new Context(conv, file)
  return conv.rules.flatMap(rule => ctx.run(rule).map(detail => ({ rule, detail })))
}

/**
 * `git status --porcelain` 의 출력에서 지금 디스크에 있는 바뀐 파일의 경로를 뽑는다.
 * 지워진 파일은 뺀다(읽을 것이 없다). 이름이 바뀐 파일은 새 이름을, 따옴표로 감싼 경로는 벗겨서 준다.
 */
export function changedPaths(porcelain: string): string[] {
  return porcelain
    .split('\n')
    .filter(line => line.length > 3 && !line.slice(0, 2).includes('D'))
    .map(line => (line.slice(3).split(' -> ').pop() as string).replace(/^"|"$/g, ''))
}

/** 위반을 모델이 읽을 문장으로. 고칠 방향(허용되는 역할과 그 뜻)까지 함께 말한다. */
export function format(path: string, violations: Violation[]): string {
  const byRule = new Map<Rule, string[]>()
  for (const v of violations) byRule.set(v.rule, [...(byRule.get(v.rule) ?? []), v.detail])

  const blocks = [...byRule].map(([rule, details]) => {
    const lines = [`■ ${rule.id} — ${rule.rule}`, ...details.map(d => `  위반: ${d}`), `  이유: ${rule.why}`]
    if (rule.check.kind === 'file-name') {
      const roles = { ...rule.check.endsWith }
      const prefixes = rule.check.startsWith ?? {}
      lines.push(`  허용되는 끝말: ${Object.entries(roles).map(([k, v]) => `${k}(${v})`).join(', ')}`)
      if (Object.keys(prefixes).length > 0) {
        lines.push(`  허용되는 앞말: ${Object.entries(prefixes).map(([k, v]) => `${k}(${v})`).join(', ')}`)
      }
    } else if (rule.ok?.length) {
      lines.push(`  예: ${rule.ok.join(', ')}`)
    }
    return lines.join('\n')
  })

  return [
    `[convention-gate] ${path} 이 .claude/conventions.json 의 규약을 어긴다.`,
    ...blocks,
    '규칙에 없는 역할이면 이름을 짓지 말고 사용자에게 묻는다. 정해지면 .claude/conventions.json 에 이유와 함께 추가한다.',
  ].join('\n')
}

class Context {
  private readonly layer: string | null
  private readonly fileName: string
  private readonly imports: Import[]
  private readonly declarations: Declaration[]

  constructor(private readonly conv: Conventions, private readonly file: SourceFile) {
    const segments = file.path.split('/')
    this.layer = layerAt(segments, conv)
    this.fileName = (segments[segments.length - 1] ?? '').replace(/\.kt$/, '')
    this.imports = parseImports(file.text, conv)
    this.declarations = parseDeclarations(file.text)
  }

  run(rule: Rule): string[] {
    const c = rule.check
    switch (c.kind) {
      case 'no-import':
        return this.layer !== c.from
          ? []
          : this.imports.filter(i => i.layer !== null && c.to.includes(i.layer)).map(i => `import ${i.fqn}`)
      case 'file-name':
        return this.fileNameViolations(c)
      case 'banned-suffix':
        return this.bannedSuffixViolations(c.suffixes)
      case 'interface-only':
        return this.layer !== c.in
          ? []
          : this.declarations
              .filter(d => d.kind !== 'interface' && c.endsWith.some(s => d.name.endsWith(s)))
              .map(d => `${d.kind} ${d.name} — interface 여야 한다`)
      case 'port-impl-name':
        return this.portImplViolations(c)
      case 'controller-deps':
        return this.controllerViolations(c)
      default:
        return [`모르는 검사 종류다: ${(c as { kind: string }).kind} — convention-gate 와 ArchitectureTest 에 검사 방법을 더한다`]
    }
  }

  private fileNameViolations(c: Extract<Check, { kind: 'file-name' }>): string[] {
    if (this.layer !== c.in || this.file.path in (c.except ?? {})) return []
    const ends = Object.keys(c.endsWith)
    const starts = Object.keys(c.startsWith ?? {})
    const fits = ends.some(s => this.fileName.endsWith(s)) || starts.some(s => this.fileName.startsWith(s))
    return fits ? [] : [`${this.fileName}.kt`]
  }

  /** 선언된 타입 이름과 파일 이름을 함께 본다. 파일 이름만 어긋나는 경우도 같은 혼란을 만든다. */
  private bannedSuffixViolations(suffixes: Record<string, string>): string[] {
    const names = new Set([...this.declarations.map(d => d.name), this.fileName])
    return [...names].flatMap(name => {
      const hit = Object.entries(suffixes).find(([suffix]) => name.endsWith(suffix))
      return hit ? [`${name} — ${hit[0]}: ${hit[1]}`] : []
    })
  }

  private portImplViolations(c: Extract<Check, { kind: 'port-impl-name' }>): string[] {
    if (this.layer !== c.implIn) return []
    const prefixes = Object.keys(c.prefixes)
    return this.declarations.flatMap(d => {
      if (d.kind === 'object') return []
      return supertypesOf(this.file.text, d)
        .map(name => this.imports.find(i => i.simpleName === name))
        .filter((port): port is Import => port !== undefined && port.layer === c.portsIn)
        .filter(port => c.portEndsWith.some(s => port.simpleName.endsWith(s)))
        .filter(port => !prefixes.some(p => d.name === p + port.simpleName))
        .map(port => `${d.name} 는 ${port.simpleName} 의 구현이다 → {${prefixes.join('|')}}${port.simpleName}`)
    })
  }

  private controllerViolations(c: Extract<Check, { kind: 'controller-deps' }>): string[] {
    if (this.layer !== c.in) return []
    return this.declarations
      .filter(d => d.kind === 'class' && d.name.endsWith(c.controllerEndsWith))
      .flatMap(d =>
        constructorParamTypes(this.file.text, d)
          .map(type => this.imports.find(i => i.simpleName === type))
          .filter((t): t is Import => t !== undefined && t.layer === c.typesFrom)
          .filter(t => !c.allowedEndsWith.some(s => t.simpleName.endsWith(s)))
          .map(t => `${d.name} 가 ${t.simpleName} 를 받는다`),
      )
  }
}

/** `<모듈>/<층>/...` 의 층. 모듈 루트(공개 포트·값)는 층이 없다. */
function layerAt(segments: string[], conv: Conventions): string | null {
  const layer = segments[1]
  return segments.length >= 3 && layer !== undefined && conv.layers.includes(layer) ? layer : null
}

function parseImports(text: string, conv: Conventions): Import[] {
  const base = `${conv.basePackage}.`
  return [...text.matchAll(/^import\s+([\w.]+)(?:\s+as\s+\w+)?\s*$/gm)].flatMap(m => {
    const fqn = m[1]
    if (!fqn) return []
    const inner = fqn.startsWith(base) ? fqn.slice(base.length).split('.') : []
    return [{ fqn, simpleName: fqn.split('.').pop() ?? fqn, layer: layerAt(inner, conv) }]
  })
}

const MODIFIER = '(?:public|internal|private|protected|open|abstract|sealed|data|enum|annotation|value|inner|inline|companion|expect|actual|fun)'
const DECLARATION = new RegExp(`^[ \\t]*(?:@[\\w.]+(?:\\([^)]*\\))?\\s+)*(?:${MODIFIER}\\s+)*(class|interface|object)\\s+([A-Za-z_]\\w*)`, 'gm')

function parseDeclarations(text: string): Declaration[] {
  return [...text.matchAll(DECLARATION)].flatMap(m =>
    m[2] ? [{ kind: m[1] as Declaration['kind'], name: m[2], start: (m.index ?? 0) + m[0].length }] : [],
  )
}

/** 선언 머리(이름 뒤 ~ 첫 `{`)에서 깊이 0 의 `:` 뒤를 상위 타입 목록으로 읽는다. */
function supertypesOf(text: string, d: Declaration): string[] {
  const header = headerOf(text, d.start)
  const colon = indexAtDepthZero(header, ':')
  if (colon < 0) return []
  return splitAtDepthZero(header.slice(colon + 1), ',')
    .map(part => part.trim().match(/^([A-Za-z_][\w.]*)/)?.[1] ?? '')
    .filter(name => name.length > 0)
    .map(name => name.split('.').pop() as string)
}

/** 주 생성자 괄호 안의 매개변수 타입. `private val x: Foo` → `Foo`. */
function constructorParamTypes(text: string, d: Declaration): string[] {
  const header = headerOf(text, d.start)
  const open = header.indexOf('(')
  if (open < 0) return []
  const close = matchingParen(header, open)
  if (close < 0) return []
  return splitAtDepthZero(header.slice(open + 1, close), ',')
    .map(param => param.match(/:\s*([A-Za-z_][\w.]*)/)?.[1] ?? '')
    .filter(type => type.length > 0)
    .map(type => type.split('.').pop() as string)
}

/** 선언 머리: 이름 뒤부터 몸체 `{` 전까지. 몸체가 없으면 빈 줄에서 끊는다. */
function headerOf(text: string, start: number): string {
  let depth = 0
  for (let i = start; i < text.length; i++) {
    const ch = text[i]
    if (ch === '(' || ch === '<') depth++
    else if (ch === ')' || ch === '>') depth--
    else if (depth === 0 && ch === '{') return text.slice(start, i)
    else if (depth === 0 && ch === '\n' && /^\n[ \t]*\n/.test(text.slice(i, i + 80))) return text.slice(start, i)
  }
  return text.slice(start)
}

function indexAtDepthZero(s: string, target: string): number {
  let depth = 0
  for (let i = 0; i < s.length; i++) {
    const ch = s[i]
    if (ch === '(' || ch === '<') depth++
    else if (ch === ')' || ch === '>') depth--
    else if (depth === 0 && ch === target) return i
  }
  return -1
}

function splitAtDepthZero(s: string, separator: string): string[] {
  const parts: string[] = []
  let depth = 0
  let from = 0
  for (let i = 0; i < s.length; i++) {
    const ch = s[i]
    if (ch === '(' || ch === '<') depth++
    else if (ch === ')' || ch === '>') depth--
    else if (depth === 0 && ch === separator) {
      parts.push(s.slice(from, i))
      from = i + 1
    }
  }
  parts.push(s.slice(from))
  return parts
}

function matchingParen(s: string, open: number): number {
  let depth = 0
  for (let i = open; i < s.length; i++) {
    if (s[i] === '(') depth++
    else if (s[i] === ')' && --depth === 0) return i
  }
  return -1
}

function trimSlash(s: string): string {
  return s.replace(/\/+$/, '')
}
