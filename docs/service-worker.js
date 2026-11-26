
const CACHE_NAME = "morning-athkar-v43";

const APP_FILES = [
  "./",
  "./index.html",
  "./styles.css",
  "./app.js",
  "./athkar.json",
  "./manifest.webmanifest",
  "./icon.svg",
  "./icons/screen-wake.png",
];

self.addEventListener("install", (event) => {
  event.waitUntil(
      caches.open(CACHE_NAME).then((cache) =>
          cache.addAll(APP_FILES)
      )
  );
  
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
      caches.keys().then((names) =>
          Promise.all(
              names
                  .filter((name) => name !== CACHE_NAME)
                  .map((name) => caches.delete(name))
          )
      )
  );
  
  self.clients.claim();
});

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;
  
  const url = new URL(event.request.url);
  
  // Externe APIs direkt über das Netzwerk laden.
  if (url.origin !== self.location.origin) return;
  
  const networkFirst =
      event.request.mode === "navigate" ||
      url.pathname.endsWith(".js") ||
      url.pathname.endsWith(".css");
  
  if (networkFirst) {
    event.respondWith(
        (async () => {
          try {
            const response = await fetch(event.request);
            
            if (response.ok) {
              const cache = await caches.open(CACHE_NAME);
              const copy = response.clone();
              
              event.waitUntil(
                  cache.put(event.request, copy).catch(() => {})
              );
            }
            
            return response;
          } catch {
            const cached = await caches.match(event.request);
            
            if (cached) return cached;
            
            if (event.request.mode === "navigate") {
              const fallback = await caches.match("./index.html");
              if (fallback) return fallback;
            }
            
            return Response.error();
          }
        })()
    );
    
    return;
  }
  
  // Andere Dateien bevorzugt aus dem Cache laden.
  event.respondWith(
      caches.match(event.request).then(
          (cached) =>
              cached ||
              fetch(event.request).then((response) => {
                if (response.ok) {
                  const copy = response.clone();
                  
                  event.waitUntil(
                      caches.open(CACHE_NAME).then((cache) =>
                          cache.put(event.request, copy)
                      ).catch(() => {})
                  );
                }
                
                return response;
              })
      )
  );
});
