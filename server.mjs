/* 预算本 · 局域网小服务器
   作用：让手机能通过 Wi-Fi 打开电脑上的这个应用，从而可以「添加到主屏幕」。
   用法：双击 启动手机版.bat，或在本目录执行 node server.mjs */
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { networkInterfaces } from 'node:os';
import { extname, join, resolve, sep } from 'node:path';

const ROOT = resolve(process.cwd());
const START_PORT = 8137;

const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.css': 'text/css; charset=utf-8',
};

function send(res, code, body, type) {
  res.writeHead(code, {
    'Content-Type': type || 'text/plain; charset=utf-8',
    'Cache-Control': 'no-store',
  });
  res.end(body);
}

// 这台机器上可能装了一堆虚拟网卡（VMware / Tailscale / VPN……），
// 全都列出来只会让人不知道该输哪个，所以先过滤再按常见程度排序。
const VIRTUAL = /vmware|vethernet|virtual|hyper|tailscale|zerotier|npcap|loopback|clash|tun\b|tap\b|wsl|docker|vpn|radmin|hamachi|bluetooth|蓝牙/i;
function score(ip) {
  if (ip.startsWith('192.168.')) return 0;
  if (ip.startsWith('10.')) return 1;
  const m = ip.match(/^172\.(\d+)\./);
  if (m && +m[1] >= 16 && +m[1] <= 31) return 2;
  return 3;
}
function lanIPs() {
  const all = [];
  const nis = networkInterfaces();
  for (const name of Object.keys(nis)) {
    for (const ni of nis[name] || []) {
      if (ni.family !== 'IPv4' || ni.internal) continue;
      if (ni.address.startsWith('169.254.')) continue;          // 没拿到 DHCP 时的自动地址，连不通
      all.push({ name, ip: ni.address, virtual: VIRTUAL.test(name) });
    }
  }
  const real = all.filter(x => !x.virtual);
  return (real.length ? real : all).sort((a, b) => score(a.ip) - score(b.ip) || a.name.localeCompare(b.name));
}

const server = createServer(async (req, res) => {
  let pathname;
  try {
    pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
  } catch {
    return send(res, 400, 'bad request');
  }
  if (pathname === '/') pathname = '/index.html';

  const file = resolve(join(ROOT, pathname));
  if (file !== ROOT && !file.startsWith(ROOT + sep)) return send(res, 403, 'forbidden');

  try {
    const buf = await readFile(file);
    send(res, 200, buf, TYPES[extname(file).toLowerCase()] || 'application/octet-stream');
  } catch {
    send(res, 404, '404 Not Found');
  }
});

let port = START_PORT;
function start() {
  server.listen(port, '0.0.0.0', () => {
    const ips = lanIPs();
    console.log('');
    console.log('  预算本 已启动');
    console.log('');
    console.log('  电脑上打开：   http://localhost:' + port);
    if (ips.length) {
      console.log('');
      console.log('  手机上打开（手机需和电脑连同一个 Wi-Fi）：');
      console.log('');
      console.log('      http://' + ips[0].ip + ':' + port + '          <== 试这个（' + ips[0].name + '）');
      for (const x of ips.slice(1)) {
        console.log('      http://' + x.ip + ':' + port + '          （' + x.name + '，上面那个不行再试）');
      }
      console.log('');
      console.log('  手机打开后，点浏览器菜单里的「添加到主屏幕 / 添加到桌面」。');
      console.log('  第一次运行 Windows 可能会弹防火墙提示，要选「允许访问」。');
    } else {
      console.log('');
      console.log('  没检测到局域网 IP，确认电脑已连上 Wi-Fi 或网线。');
    }
    console.log('');
    console.log('  停止：按 Ctrl+C，或直接关掉这个窗口');
    console.log('');
  });
}

server.on('error', e => {
  if (e.code === 'EADDRINUSE' && port < START_PORT + 20) {
    port++;
    start();
  } else {
    console.error('启动失败：' + e.message);
    process.exit(1);
  }
});

start();
