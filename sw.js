/* 预算本 离线缓存
   策略：预缓存 + 「缓存优先，后台更新」
   · 第一次打开就把全部资源存进手机
   · 之后断网也是秒开（直接从缓存拿，不等网络）
   · 有网时静默拉新版，下次打开就是新的
*/
const CACHE = 'budgetbook-v3';

const ASSETS = [
  './',
  './index.html',
  './manifest.webmanifest',
  './favicon-32.png',
  './icon-192.png',
  './icon-512.png',
  './icon-maskable-512.png',
  './apple-touch-icon.png'
];

self.addEventListener('install', e => {
  e.waitUntil((async () => {
    const c = await caches.open(CACHE);
    // 逐个加，某个文件缺失不至于让整个安装失败
    await Promise.all(ASSETS.map(u =>
      c.add(new Request(u, { cache: 'reload' })).catch(() => {})
    ));
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', e => {
  e.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)));
    await self.clients.claim();
  })());
});

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;

  let url;
  try { url = new URL(req.url); } catch (err) { return; }
  if (url.origin !== location.origin) return;

  e.respondWith((async () => {
    const c = await caches.open(CACHE);
    const hit = await c.match(req, { ignoreSearch: true });

    // 后台更新：不管缓存有没有，都试着拉一次，成功就写回
    const refresh = fetch(req).then(res => {
      if (res && res.ok) c.put(req, res.clone()).catch(() => {});
      return res;
    }).catch(() => null);

    if (hit) return hit;                  // 有缓存就先给，不等待网络

    const res = await refresh;
    if (res) return res;

    // 彻底断网又没有缓存时，导航请求兜底给首页
    if (req.mode === 'navigate') {
      const home = await c.match('./index.html');
      if (home) return home;
    }
    return new Response('离线，且没有缓存', {
      status: 503, headers: { 'Content-Type': 'text/plain; charset=utf-8' }
    });
  })());
});
