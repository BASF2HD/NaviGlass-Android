// Local UI review only. No credentials, music writes, or APK packaging.
import http from "node:http";
import https from "node:https";
import fs from "node:fs/promises";
import path from "node:path";
import dns from "node:dns/promises";
import { fileURLToPath } from "node:url";
const assets = fileURLToPath(
  new URL("../app/src/main/assets/", import.meta.url),
);
const starter = JSON.parse(
    await fs.readFile(path.join(assets, "radio-stations.json"), "utf8"),
  ),
  cache = new Map();
function publicAddress(ip) {
  if (ip.includes(":")) return false;
  return !/^(0|10|127|169\.254|172\.(1[6-9]|2[0-9]|3[01])|192\.168|100\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])|224|23[0-9]|24[0-9]|25[0-5])\./.test(
    ip,
  );
}
async function get(address, limit) {
  const url = new URL(address);
  if (
    url.protocol !== "https:" ||
    url.username ||
    url.password ||
    (url.port && url.port !== "443")
  )
    throw Error("HTTPS image required");
  const addresses = await dns.lookup(url.hostname, { all: true });
  const selected = addresses.find(
    (a) => a.family === 4 && publicAddress(a.address),
  );
  if (
    !selected ||
    addresses.some((a) => a.family === 4 && !publicAddress(a.address))
  )
    throw Error("Public address required");
  return new Promise((resolve, reject) => {
    const req = https.get(
      url,
      {
        family: 4,
        autoSelectFamily: false,
        lookup: (host, options, callback) =>
          callback(null, selected.address, 4),
        headers: {
          "User-Agent": "NaviGlass-Local-Preview",
          Accept: "image/*, application/json",
        },
      },
      (response) => {
        if (response.statusCode !== 200) {
          response.resume();
          reject(Error("Resource unavailable"));
          return;
        }
        let total = 0;
        const chunks = [];
        response.on("data", (chunk) => {
          total += chunk.length;
          if (total > limit) {
            req.destroy(Error("Response too large"));
            return;
          }
          chunks.push(chunk);
        });
        response.on("end", () =>
          resolve({
            bytes: Buffer.concat(chunks),
            type: response.headers["content-type"] || "",
          }),
        );
        response.on("error", reject);
      },
    );
    req.setTimeout(8000, () => req.destroy(Error("Resource timed out")));
    req.on("error", reject);
  });
}
async function artwork(id) {
  if (cache.has(id)) return cache.get(id);
  let station = starter.find((s) => s.id === id);
  if (!station) {
    if (!/^radio:[a-f0-9-]{36}$/.test(id)) throw Error("Unknown station");
    const data = await get(
      "https://de1.api.radio-browser.info/json/stations/byuuid/" + id.slice(6),
      262144,
    );
    station = JSON.parse(data.bytes.toString("utf8"))[0];
  }
  const result = await get(station.favicon, 3 * 1024 * 1024);
  if (
    !/^image\/(jpeg|png|webp|gif|x-icon|vnd.microsoft.icon)/i.test(result.type)
  )
    throw Error("Unsupported artwork");
  if (cache.size >= 200) cache.delete(cache.keys().next().value);
  cache.set(id, result);
  return result;
}
const server = http.createServer(async (req, res) => {
  try {
    if (!["GET", "HEAD"].includes(req.method)) {
      res.writeHead(405);
      res.end();
      return;
    }
    const name = decodeURIComponent(
      new URL(req.url, "http://127.0.0.1").pathname,
    );
    if (name === "/__preview") {
      res.setHeader("Content-Type", "application/json");
      res.end(JSON.stringify({ mode: "UI review", version: "0.2.5", nativeLogin: false }));
      return;
    }
    if (name.startsWith("/radio-art/")) {
      const id = name.slice("/radio-art/".length).replace(/\.jpg$/, "");
      const result = await artwork(id);
      res.setHeader("Content-Type", result.type);
      res.setHeader("Cache-Control", "public,max-age=1800");
      res.setHeader("X-Content-Type-Options", "nosniff");
      res.end(req.method === "HEAD" ? undefined : result.bytes);
      return;
    }
    const filename = path.resolve(
      assets,
      name === "/" ? "index.html" : "." + name,
    );
    if (!filename.startsWith(assets)) {
      res.writeHead(403);
      res.end();
      return;
    }
    const mime = {
      ".html": "text/html",
      ".js": "text/javascript",
      ".css": "text/css",
      ".svg": "image/svg+xml",
      ".json": "application/json",
      ".png": "image/png",
      ".jpg": "image/jpeg",
    }[path.extname(filename)];
    if (!mime) {
      res.writeHead(404);
      res.end();
      return;
    }
    const bytes = await fs.readFile(filename);
    res.setHeader("Content-Type", mime);
    res.setHeader("Cache-Control", "no-cache");
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.end(req.method === "HEAD" ? undefined : bytes);
  } catch {
    res.writeHead(404);
    res.end();
  }
});
server.listen(8769, "127.0.0.1", () =>
  console.log(
    "NaviGlass UI review: http://127.0.0.1:8769/?v=0.2.5 — local UI review; Navidrome login is native-only",
  ),
);
process.on("SIGTERM", () => server.close());
