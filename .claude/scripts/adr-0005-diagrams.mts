// ADR 0005 의 그림 docs/images/adr-0005-*.png 를 만든다.
//
//   node .claude/scripts/adr-0005-diagrams.mts
//
// 도면 라이브러리와 렌더러는 저장소 밖의 excalidraw-diagram 스킬에 있다. 다른 자리에 있으면 EXCALIDRAW_SKILL 로 알린다.
// PNG 는 rsvg-convert 가 만든다(brew install librsvg).
// SVG 가 아니라 PNG 를 두는 이유 — 렌더러의 SVG 는 글꼴을 담지 않아 보는 사람의 시스템 글꼴에 따라 글자 폭이 달라진다.
import { execFileSync } from 'node:child_process';
import { copyFileSync, mkdtempSync } from 'node:fs';
import { homedir, tmpdir } from 'node:os';
import { join } from 'node:path';

const SKILL = process.env.EXCALIDRAW_SKILL ?? join(homedir(), '.claude/skills/excalidraw-diagram');
const { Drawing } = await import(join(SKILL, 'lib/excalidraw.ts'));

const OUT = join(import.meta.dirname, '../../docs/images');
const TMP = mkdtempSync(join(tmpdir(), 'adr-0005-'));

/** 도면을 검사해 저장하고 PNG 로 그려 docs/images 에 둔다. 검사에 걸리면 save 가 목록을 내고 끝낸다. */
function render(d: any, name: string) {
    const src = join(TMP, `${name}.excalidraw.md`);
    d.save(src);
    execFileSync('node', [join(SKILL, 'bin/preview.ts'), src, join(TMP, `${name}.png`), '--scale=2']);
    copyFileSync(join(TMP, `${name}.png`), join(OUT, `${name}.png`));
    console.log(join(OUT, `${name}.png`));
}

// ── 그림 1. 구조 — 누가 어떤 토큰으로 누구를 부르는가 ─────────────────────────
{
    const d = new Drawing();
    d.title('ADR 0005 — 구조', '사람의 토큰은 authserver 가 발급하고, API 는 JWKS 로만 검증한다');

    const ROW = 76;
    const [y1, y2, y3, y4] = [180, 290, 450, 560];

    const service = d.box(40, y1, '내 다른 서비스', 'actor', { w: 220, h: ROW });
    const browser = d.box(40, y2, '브라우저 (콘솔)\n접근 토큰은 메모리에만', 'actor', { w: 220, h: ROW });
    const cli = d.box(40, y4, 'CLI · MCP 클라이언트\n(2단계)', 'actor', { w: 220, h: ROW });

    const backend = d.box(350, y3, '콘솔 백엔드\n토큰 중개 · 기밀 클라이언트', 'added', { w: 240, h: ROW });
    const api = d.box(740, y1, '워크벤치 API\n리소스 서버 (auth 모듈)', 'existing', { w: 240, h: y2 + ROW - y1 });
    const authserver = d.box(740, y3, 'authserver 모듈\n인가 · 토큰 발급 · JWKS', 'added', { w: 240, h: y4 + ROW - y3 });
    const google = d.box(authserver.cx - 85, authserver.bottom + 110, 'Google\n사용자 확인', 'plain', { w: 170, h: ROW });

    d.frame('워크벤치 (같은 앱)', 'existing', [backend, api, authserver]);

    d.connect(service, api, { label: '서비스 API 키 (ADR 0004)' });
    d.connect(browser, api, { label: 'Bearer 접근 토큰' });
    d.connect(browser, backend, { label: '리프레시 쿠키', from: 'bottom', to: 'left' });
    d.connect(backend, authserver, { label: '/oauth2/token' });
    d.connect(cli, authserver, { label: '인가 코드 + PKCE + resource' });
    d.connect(api, authserver, { label: 'JWKS 공개키', kind: 'muted' });
    d.connect(authserver, google, { label: '연합 로그인' });

    d.note(40, google.y, [
        '· 콘솔 백엔드는 같은 앱이어도 토큰 엔드포인트를 HTTP 로 부른다',
        '  — 인가 서버를 떼어 낼 때 바뀌는 것은 주소뿐이다',
        '· 리프레시 토큰은 스크립트가 읽지 못하는 쿠키에 있고,',
        '  콘솔 세션 엔드포인트(갱신 · 로그아웃)로만 간다',
    ].join('\n'));
    render(d, 'adr-0005-structure');
}

// ── 그림 2. 콘솔의 로그인과 갱신 ─────────────────────────────────────────────
{
    const d = new Drawing();
    d.title('ADR 0005 — 콘솔의 로그인과 갱신', 'OAuth 흐름은 콘솔 백엔드와 authserver 사이에만 있다');

    const s = d.sequence(40, 150, [
        ['브라우저 (콘솔)', 'actor'],
        ['콘솔 백엔드', 'added'],
        ['authserver', 'added'],
        ['Google', 'plain'],
    ], { gap: 110 });
    const [BROWSER, BACKEND, AUTH, GOOGLE] = [0, 1, 2, 3];

    s.msg(BROWSER, BACKEND, '로그인 시작');
    s.msg(BACKEND, AUTH, '인가 요청 (인가 코드 + PKCE)');
    s.msg(AUTH, GOOGLE, '사용자 확인');
    s.reply(GOOGLE, AUTH, 'Google 정체성');
    s.reply(AUTH, BACKEND, '인가 코드');
    s.msg(BACKEND, AUTH, '/oauth2/token (client_secret_basic)');
    s.reply(AUTH, BACKEND, '접근 토큰 + 리프레시 토큰');
    s.reply(BACKEND, BROWSER, '접근 토큰 + 리프레시 쿠키');

    s.note([BROWSER, BACKEND], '새로고침 · 접근 토큰 만료', 'event');
    s.msg(BROWSER, BACKEND, '갱신 (리프레시 쿠키)');
    s.msg(BACKEND, AUTH, '/oauth2/token (refresh_token)');
    s.reply(AUTH, BACKEND, '새 토큰 쌍 (회전)');
    s.reply(BACKEND, BROWSER, '새 접근 토큰 + 새 쿠키');
    const bottom = s.end();

    d.note(40, bottom + 30, [
        '· 번호가 붙은 실선은 요청, 회색 점선은 응답이다. ② 와 인가 코드 응답은 브라우저 리다이렉트로 오간다',
        '· 브라우저가 갖는 것은 접근 토큰(메모리)과 스크립트가 읽지 못하는 리프레시 쿠키뿐이다',
    ].join('\n'));
    render(d, 'adr-0005-console-login');
}

// ── 그림 3. 리프레시 토큰이 왔을 때 ──────────────────────────────────────────
{
    const d = new Drawing();
    d.title('ADR 0005 — 리프레시 토큰이 왔을 때', '물러난 토큰을 기억해 동시 요청과 탈취를 가른다');

    const QUESTIONS = ['살아 있는\n토큰인가?', '물러난 토큰으로\n기억하고 있나?', '바로 직전 토큰이고\n겹침 시간 안인가?'];
    // 마름모 너비는 글자로 정해진다. 세로로 한 줄에 세우려면 그리기 전에 너비를 알아야 한다
    const widths = QUESTIONS.map((q) => new Drawing().decision(0, 0, q).w);
    const CX = 40 + Math.max(...widths) / 2;
    const OUT_W = 310;
    const OUT_X = CX + Math.max(...widths) / 2 + 100;

    const req = d.box(CX - 110, 150, '갱신 요청\n리프레시 토큰 도착', 'actor', { w: 220 });
    let y = req.bottom + 60;
    const [q1, q2, q3] = QUESTIONS.map((q, i) => {
        const n = d.decision(CX - widths[i] / 2, y, q);
        y = n.bottom + 76;
        return n;
    });
    const beside = (q: any, label: string, role: string) => d.box(OUT_X, q.cy - 36, label, role, { w: OUT_W, h: 72 });
    const rotate = beside(q1, '회전 — 새 쌍을 발급한다\n쓴 토큰은 해시로 남긴다', 'result');
    const reject = beside(q2, '거절 (invalid_grant)\n처음 보는 토큰 · 만료돼 지운 토큰', 'risk');
    const resend = beside(q3, '동시 요청으로 본다\n방금 발급한 쌍을 다시 준다', 'result');
    const revoke = d.box(CX - OUT_W / 2, y, '탈취로 본다\n그 인가의 살아 있는 토큰까지 폐기', 'risk', { w: OUT_W, h: 72 });

    d.connect(req, q1);
    d.connect(q1, q2, { label: '아니요' });
    d.connect(q2, q3, { label: '예' });
    d.connect(q1, rotate, { label: '예' });
    d.connect(q2, reject, { label: '아니요', kind: 'risk' });
    d.connect(q3, resend, { label: '예' });
    d.connect(q3, revoke, { label: '아니요', kind: 'risk' });

    d.note(40, revoke.bottom + 50, [
        '· 물러난 토큰의 해시는 인가마다 모두 둔다',
        '  — 직전 하나만 두면 공격자가 두 번 회전한 뒤에는 감지하지 못한다',
        '· 물러난 토큰은 그 토큰이 만료되면 지운다. 인가가 끝나면 함께 지운다',
    ].join('\n'));
    render(d, 'adr-0005-refresh-rotation');
}
