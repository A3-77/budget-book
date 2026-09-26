/* 打包安卓 APK
   用法：node build-apk.mjs  （或双击 打包APK.bat）
   做三件事：清理 assets → 调 Gradle 打包 → 把 APK 拷到根目录 */
import { copyFileSync, mkdirSync, existsSync, rmSync, statSync, writeFileSync, readdirSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { join } from 'node:path';

const ROOT = process.cwd();
const ANDROID = join(ROOT, 'android');
const ASSETS = join(ANDROID, 'app/src/main/assets');

function findJavaHome() {
  if (process.env.JAVA_HOME && existsSync(join(process.env.JAVA_HOME, 'bin/java.exe'))) return process.env.JAVA_HOME;
  for (const base of ['C:/Program Files/Eclipse Adoptium', 'C:/Program Files/Java', 'C:/Program Files/Microsoft']) {
    if (!existsSync(base)) continue;
    for (const d of readdirSync(base)) {
      const p = join(base, d);
      if (existsSync(join(p, 'bin/java.exe'))) return p;
    }
  }
  return null;
}

function findSdk() {
  const candidates = [
    process.env.ANDROID_HOME,
    process.env.ANDROID_SDK_ROOT,
    join(process.env.LOCALAPPDATA || '', 'Android/Sdk'),
  ].filter(Boolean);
  for (const c of candidates) {
    if (existsSync(join(c, 'platforms'))) return c;
  }
  return null;
}

/* ---------- 1. 清理 assets ---------- */
console.log('\n[1/3] 清理 assets（界面已改成原生实现，不再需要网页资源）…');
rmSync(ASSETS, { recursive: true, force: true });
mkdirSync(ASSETS, { recursive: true });
console.log('      已清空');

/* ---------- 2. 环境检查 ---------- */
const javaHome = findJavaHome();
const sdk = findSdk();
if (!javaHome) { console.error('\n找不到 JDK，请先装 JDK 17 或更高版本。'); process.exit(1); }
if (!sdk) { console.error('\n找不到 Android SDK（应包含 platforms/ 目录）。'); process.exit(1); }
writeFileSync(join(ANDROID, 'local.properties'), 'sdk.dir=' + sdk.replace(/\\/g, '/') + '\n');

const gradle = process.env.GRADLE_BIN || join(process.env.LOCALAPPDATA || '', 'gradle-8.9/bin/gradle.bat');
if (!existsSync(gradle)) {
  console.error('\n找不到 Gradle：' + gradle);
  console.error('请下载 gradle-8.9-bin.zip 解压到 %LOCALAPPDATA%\\gradle-8.9，或设置环境变量 GRADLE_BIN。');
  process.exit(1);
}

console.log('[2/3] 开始打包（第一次会比较慢，要下载构建依赖）…\n');
console.log('      JDK    : ' + javaHome);
console.log('      SDK    : ' + sdk);
console.log('      Gradle : ' + gradle + '\n');

const env = { ...process.env, JAVA_HOME: javaHome, ANDROID_HOME: sdk, ANDROID_SDK_ROOT: sdk };

function runGradle(task) {
  return spawnSync('"' + gradle + '"', [task, '--console=plain'], {
    cwd: ANDROID,
    env,
    stdio: 'inherit',
    shell: true,
  });
}

let variant = 'release';
let r = runGradle('assembleRelease');
if (r.status !== 0) {
  console.log('\n  release 打包失败，改用 debug 重试…\n');
  variant = 'debug';
  r = runGradle('assembleDebug');
}
if (r.status !== 0) {
  console.error('\n打包失败，退出码 ' + r.status);
  process.exit(r.status || 1);
}

/* ---------- 3. 取出 APK ---------- */
const built = join(ANDROID, 'app/build/outputs/apk', variant, 'app-' + variant + '.apk');
if (!existsSync(built)) {
  console.error('\n没找到产物：' + built);
  process.exit(1);
}

const out = join(ROOT, '预算本.apk');
copyFileSync(built, out);
const mb = (statSync(out).size / 1024 / 1024).toFixed(2);

console.log('\n[3/3] 完成');
console.log('      ' + out + '   (' + mb + ' MB, ' + variant + ')');
console.log('\n  把这个 APK 传到手机（微信/QQ/数据线都行），点开安装即可。\n');
