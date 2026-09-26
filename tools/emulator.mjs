/* 打开安卓模拟器并运行「预算本」
   由 start-emulator.bat 调用；也可以直接 node tools/emulator.mjs
   每一步都会实时写进「启动日志.txt」，卡住时看那个文件就知道停在哪。 */
import { spawn, spawnSync } from 'node:child_process';
import { existsSync, writeFileSync, openSync, closeSync, readFileSync, unlinkSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

const ROOT = process.cwd();
const LOG = join(ROOT, '启动日志.txt');
const SDK = join(process.env.LOCALAPPDATA || '', 'Android', 'Sdk');
const EMU = join(SDK, 'emulator', 'emulator.exe');
const ADB = join(SDK, 'platform-tools', 'adb.exe');
const AVD = 'budgettest';
const SERIAL = 'emulator-5554';
const PKG = 'com.budgetbook.app';
const APK = join(ROOT, '预算本.apk');
const EMU_LOG = join(tmpdir(), 'budgetbook-emulator.log');
const ADB_OUT = join(tmpdir(), 'budgetbook-adb-out.txt');

// 用软件渲染（swiftshader）而不是 host GPU。
// 这台机器上 host 模式偶发 "Failed to load opengl32sw"，模拟器会直接起不来；
// swiftshader 连续冷启动都稳。界面是普通原生控件，软件渲染完全够用。
const EMU_ARGS = ['-avd', AVD, '-no-boot-anim', '-memory', '2048', '-gpu', 'swiftshader_indirect'];

const lines = [];
function out(s) {
  lines.push(s);
  console.log(s);
  flush();                     // 每写一行就落盘，免得卡住时什么都看不到
}
function flush() {
  try {
    writeFileSync(LOG, lines.join('\n') + '\n', 'utf8');
  } catch (e) { /* 忽略 */ }
}
function fail(msg) {
  if (msg) out(msg);
  out('');
  out('  详细信息已写入：' + LOG);
  process.exit(1);
}

const sleep = ms => Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);

/**
 * 跑一次 adb 并拿到输出。
 * 关键：不能用管道。adb 在 server 没起来时会 fork 一个守护进程，
 * 守护进程会继承 stdout 管道，Node 的 spawnSync 会一直等管道关闭 —— 直接卡死。
 * 所以把输出写到临时文件再读，跟管道彻底无关。
 */
function adb(args) {
  try { unlinkSync(ADB_OUT); } catch (e) { /* 忽略 */ }
  let fd = -1;
  const t0 = Date.now();
  try {
    fd = openSync(ADB_OUT, 'w');
    const r = spawnSync(ADB, args, { stdio: ['ignore', fd, fd], timeout: 60000 });
    if (r.error) out('  (adb ' + args[0] + ' 出错：' + r.error.message + ')');
  } catch (e) {
    out('  (adb ' + args[0] + ' 异常：' + e.message + ')');
  } finally {
    if (fd >= 0) { try { closeSync(fd); } catch (e) { /* 忽略 */ } }
  }
  const ms = Date.now() - t0;
  if (ms > 3000) out('  (adb ' + args.slice(0, 2).join(' ') + ' 用了 ' + ms + ' ms)');
  try {
    return readFileSync(ADB_OUT, 'utf8').trim();
  } catch (e) {
    return '';
  }
}

try {
  out('');
  out('  预算本 · 安卓模拟器');
  out('  时间：' + new Date().toLocaleString('zh-CN'));
  out('');

  if (!existsSync(EMU)) {
    fail('  [!] 找不到安卓模拟器：\n      ' + EMU + '\n      可能被删掉了，重装方法见 README。');
  }
  if (!existsSync(ADB)) {
    fail('  [!] 找不到 adb：\n      ' + ADB);
  }

  out('  准备 adb…');
  adb(['start-server']);

  /* ---- 1. 已经在跑就直接用 ---- */
  const devices = adb(['devices']);
  const running = devices.split('\n').some(l => l.trim().startsWith(SERIAL) && l.includes('device'));
  out('  检测到设备：' + (devices.split('\n').filter(l => l.includes('emulator')).join(' / ') || '（没有）'));

  if (running) {
    out('  模拟器已经在运行了，直接打开「预算本」…');
  } else {
    out('  正在启动安卓模拟器…');
    out('  第一次开机要 30~60 秒，之后就快了。');

    let fd;
    try {
      fd = openSync(EMU_LOG, 'w');
    } catch (e) {
      fd = 'ignore';
    }

    let child;
    try {
      child = spawn(EMU, EMU_ARGS, {
        detached: true,
        stdio: ['ignore', fd, fd],
        cwd: ROOT,
      });
    } catch (e) {
      fail('  [!] 启动模拟器失败：' + e.message);
    }
    child.on('error', e => {
      fail('  [!] 启动模拟器失败：' + e.message + '\n      模拟器路径：' + EMU);
    });
    child.unref();

    /* ---- 2. 等它出现在 adb 里；偶尔会起不来，自动再试一次 ---- */
    let appeared = false;
    for (let attempt = 1; attempt <= 2 && !appeared; attempt++) {
      if (attempt === 2) {
        out('  第一次没起来，重试一次…');
        adb(['-s', SERIAL, 'emu', 'kill']);
        spawnSync('taskkill', ['/F', '/IM', 'qemu-system-x86_64.exe'], { stdio: 'ignore' });
        spawnSync('taskkill', ['/F', '/IM', 'emulator.exe'], { stdio: 'ignore' });
        sleep(5000);
        try { fd = openSync(EMU_LOG, 'w'); } catch (e) { fd = 'ignore'; }
        const again = spawn(EMU, EMU_ARGS, { detached: true, stdio: ['ignore', fd, fd], cwd: ROOT });
        again.on('error', () => {});
        again.unref();
      }
      for (let i = 0; i < 24; i++) {
        sleep(2500);
        process.stdout.write('\r  等待模拟器出现… ' + Math.round((i + 1) * 2.5) + ' 秒');
        if (adb(['devices']).includes(SERIAL)) { appeared = true; break; }
      }
      process.stdout.write('\r');
    }

    if (!appeared) {
      let tail = '';
      try {
        tail = readFileSync(EMU_LOG, 'utf8').split('\n').filter(l => /Critical|Warning|ERROR|error/i.test(l)).slice(-8).join('\n');
      } catch (e) { /* 忽略 */ }
      fail('  [!] 模拟器没能启动（试了两次）。\n\n  模拟器报的错：\n' + (tail || '（日志里没有明显错误）') +
           '\n\n  可以自己跑一遍看完整输出：\n      ' + EMU + ' ' + EMU_ARGS.join(' '));
    }
    out('  模拟器已启动');
    /* 用弹出来的那个模拟器窗口看就行 */
  }

  /* ---- 3. 等系统开机完成 ---- */
  const t0 = Date.now();
  let ready = false;
  while (Date.now() - t0 < 300000) {
    process.stdout.write('\r  等待开机… ' + Math.round((Date.now() - t0) / 1000) + ' 秒');
    if (adb(['-s', SERIAL, 'shell', 'getprop', 'sys.boot_completed']).includes('1')) { ready = true; break; }
    sleep(3000);
  }
  process.stdout.write('\r');
  if (!ready) {
    fail('  [!] 等太久了，模拟器还没开机完成。\n      过一会儿再双击一次，或者先把模拟器窗口关掉重来。');
  }
  out('  开机完成（' + Math.round((Date.now() - t0) / 1000) + ' 秒）');

  /* ---- 4. 有新版 APK 就装上 ---- */
  if (existsSync(APK)) {
    process.stdout.write('  安装最新版 APK…');
    const r = adb(['-s', SERIAL, 'install', '-r', APK]);
    out(r.toLowerCase().includes('success') ? ' 好了' : ' 出问题了：' + r.split('\n').pop());
  } else {
    out('  没找到 预算本.apk，跳过安装');
  }

  /* ---- 5. 打开应用 ---- */
  if (adb(['-s', SERIAL, 'shell', 'pm', 'list', 'packages', PKG]).includes(PKG)) {
    adb(['-s', SERIAL, 'shell', 'am', 'start', '-n', PKG + '/.MainActivity']);
    out('');
    out('  好了，看模拟器窗口就行。');
  } else {
    out('');
    out('  模拟器里还没装这个应用 —— 把 预算本.apk 拖进模拟器窗口就能装。');
  }
  out('');
} catch (e) {
  fail('  [!] 意外错误：' + (e && e.stack ? e.stack : e));
}
