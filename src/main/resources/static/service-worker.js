// 競馬予想アプリ用 Service Worker
//
// やっていることは2つだけ：
//   1. ページ本体(HTML) は「まずネットワーク、失敗したら前回のキャッシュ、
//      それも無ければオフライン案内ページ」の順で返す（＝出先で電波が
//      不安定でも、直前に見た出馬表や予想はオフラインで開ける）
//   2. CSS・アイコン・外部CDN(Bootstrap)などの静的ファイルは「まずキャッシュ、
//      無ければネットワークに取りに行ってキャッシュに保存」で返す
//
// 新しい予想データそのものはキャッシュしない設計（サーバ側APIの応答は
// 対象外）。あくまで「見た目の画面が開けなくて困る」を防ぐためのもの。

const CACHE_VERSION = 'v1';
const CACHE_NAME = `keiba-app-shell-${CACHE_VERSION}`;
const OFFLINE_URL = '/offline.html';

// インストール時に最低限のシェルだけ先読みしておく（同一オリジンのみ）
const PRECACHE_URLS = [
  '/manifest.json',
  '/css/style.css',
  '/offline.html',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then((cache) => cache.addAll(PRECACHE_URLS))
      .catch(() => {}) // 1件でも取得に失敗してインストール自体が止まらないように
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return; // POST等（予想の記録・お気に入り登録など）はそのまま素通し

  // ページ遷移（HTMLナビゲーション）：ネットワーク優先
  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .then((response) => {
          const copy = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(request, copy));
          return response;
        })
        .catch(() =>
          caches.match(request).then((cached) => cached || caches.match(OFFLINE_URL))
        )
    );
    return;
  }

  // それ以外の静的リソース：キャッシュ優先
  event.respondWith(
    caches.match(request).then((cached) => {
      if (cached) return cached;
      return fetch(request)
        .then((response) => {
          if (response && response.status === 200) {
            const copy = response.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(request, copy));
          }
          return response;
        })
        .catch(() => cached);
    })
  );
});
