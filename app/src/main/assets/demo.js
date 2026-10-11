import { buildPlaybackOrder, playbackStep } from "./playback-order.js";
// Synthetic design-preview data. Never used in the native Android player.
import { deliver } from "./bridge.js";
const names = [
  "Afterglow",
  "Blue Hour",
  "Quiet Cities",
  "Parallel Lines",
  "Slow Morning",
  "Night Drive",
  "Wildflowers",
  "Open Water",
  "Sunroom",
  "Wavelengths",
  "Daydreams",
  "Northbound",
];
function cover(i) {
  const colour = [
    "#c18465",
    "#647dc1",
    "#749c87",
    "#b89457",
    "#9f6b9c",
    "#6996af",
  ][i % 6];
  return (
    "data:image/svg+xml," +
    encodeURIComponent(
      `<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512"><rect width="512" height="512" fill="${colour}"/><circle cx="320" cy="180" r="180" fill="#ffffff" opacity=".1"/><path d="M-40 370L260 70L570 380V560H-40Z" fill="#172234" opacity=".5"/><path d="M-40 490L310 210L570 450V560H-40Z" fill="#101828" opacity=".7"/><text x="35" y="410" fill="#eee8de" font-family="sans-serif" font-size="38" font-weight="bold">${names[i]}</text><text x="38" y="445" fill="#d7d7d7" font-family="sans-serif" font-size="16" letter-spacing="4">NAVIGLASS DEMO</text></svg>`,
    )
  );
}
const albums = names.map((title, i) => ({
  id: "demo-" + i,
  title,
  artist: [
    "The Glass Ensemble",
    "Orbit Club",
    "North Coast Trio",
    "Quiet Hours",
  ][i % 4],
  year: String(2014 + i),
  count: 8,
  coverId: "demo-" + i,
  demoCover: cover(i),
}));
const savedRadio = new Map();
try {
  for (const station of JSON.parse(
    localStorage.getItem("naviglass-saved-radio") || "[]",
  ))
    if (station?.id && station?.url?.startsWith("https://"))
      savedRadio.set(station.id, station);
} catch {}
const favourites = new Set(),
  playlists = [];
let state = {
    playing: false,
    position: 0,
    duration: 0,
    shuffle: false,
    repeat: 0,
  },
  queue = [],
  order = [],
  albumOrder = [];
function song(album, i) {
  return {
    id: album.id + "-song-" + i,
    albumId: album.id,
    title: [
      "First Light",
      "Somewhere New",
      "Hold the Horizon",
      "Little Things",
      "Into the Blue",
      "Homeward",
      "Golden Days",
      "After Hours",
    ][i],
    artist: album.artist,
    album: album.title,
    year: album.year,
    number: i + 1,
    duration: 225000,
    favourite: favourites.has(album.id + "-song-" + i),
    composer: "The Glass Ensemble",
    albumArtist: album.artist,
    genre: "Instrumental",
    mime: "audio/flac",
    suffix: "FLAC",
    bitRate: 1411,
    samplingRate: 44100,
    bitDepth: 16,
    channels: 2,
    size: 39690000,
    disc: 1,
    file:
      "Demo library/" +
      album.title +
      "/" +
      String(i + 1).padStart(2, "0") +
      " - " +
      [
        "First Light",
        "Somewhere New",
        "Hold the Horizon",
        "Little Things",
        "Into the Blue",
        "Homeward",
        "Golden Days",
        "After Hours",
      ][i] +
      ".flac",
    sourceLabel: "Synthetic browser preview",
  };
}
const all = albums.flatMap((a) =>
  Array.from({ length: 8 }, (_, i) => song(a, i)),
);
async function radioSearch(params) {
  let last;
  for (const host of [
    "de1.api.radio-browser.info",
    "de2.api.radio-browser.info",
  ]) {
    try {
      const r = await fetch(
        "https://" + host + "/json/stations/search?" + params,
        { signal: AbortSignal.timeout(6000) },
      );
      if (!r.ok) throw Error("Radio directory unavailable");
      return await r.json();
    } catch (e) {
      last = e;
    }
  }
  throw last;
}
const stations = await fetch("./radio-stations.json").then((r) => {
  if (!r.ok) throw new Error("Cannot load radio directory");
  return r.json();
});
for (const s of savedRadio.values())
  if (!stations.some((item) => item.id === s.id)) stations.push(s);
const audio = new Audio();
let live = false;
function stopRadio() {
  live = false;
  audio.pause();
  audio.removeAttribute("src");
  audio.load();
  state.buffering = false;
  state.error = "";
}
function radio(id, ids) {
  const s = stations.find((s) => s.id === id);
  if (!s) throw new Error("Unknown station");
  stopRadio();
  live = true;
  queue = ids || stations.slice(0, 4).map((s) => s.id);
  state.track = {
    id: s.id,
    title: s.title,
    artist: s.provider + " · Live",
    album: "Internet radio",
    radio: true,
  };
  state.position = 0;
  state.duration = 0;
  state.shuffle = false;
  state.repeat = 0;
  state.playing = false;
  state.buffering = true;
  audio.src = s.url;
  audio.play().catch(failed);
  emit();
}
function failed() {
  if (!live) return;
  state.playing = false;
  state.buffering = false;
  state.error =
    "Station unavailable. Check your internet connection or try another station.";
  emit();
}
audio.addEventListener("playing", () => {
  if (live) {
    state.playing = true;
    state.buffering = false;
    state.error = "";
    emit();
  }
});
audio.addEventListener("waiting", () => {
  if (live) {
    state.buffering = true;
    emit();
  }
});
audio.addEventListener("pause", () => {
  if (live) {
    state.playing = false;
    state.buffering = false;
    emit();
  }
});
audio.addEventListener("error", failed);
function startSong(index) {
  const track = all.find((t) => t.id === queue[index]);
  if (!track) return;
  state.track = { ...track };
  state.duration = track.duration;
  state.position = 0;
  state.playing = true;
}
function advance(direction, automatic = false) {
  const current = queue.indexOf(state.track?.id);
  const mode = state.repeat === 1 ? "song" : state.repeat === 2 ? "all" : "off";
  const target = playbackStep(order, current, direction, mode, automatic);
  if (target !== null) {
    startSong(target);
    emit();
    return;
  }
  if (
    !state.shuffle &&
    (state.repeat === 0 || (!automatic && state.repeat === 1))
  ) {
    const album = albumOrder.indexOf(state.track?.albumId),
      next = albumOrder[album + direction];
    if (album >= 0 && next) {
      queue = all.filter((t) => t.albumId === next).map((t) => t.id);
      order = buildPlaybackOrder(
        queue.length,
        direction < 0 ? queue.length - 1 : 0,
        false,
      );
      startSong(direction < 0 ? queue.length - 1 : 0);
      emit();
      return;
    }
  }
  if (automatic) state.playing = false;
  else if (direction < 0) state.position = 0;
  emit();
}

function emit() {
  if (state.track) state.track.favourite = favourites.has(state.track.id);
  deliver({ type: "state", data: state });
}
window.nativeBridge = {
  postMessage(raw) {
    const req = JSON.parse(raw);
    let data = {};
    if (req.action === "radioSearch") {
      const params = new URLSearchParams({
        limit: "60",
        offset: String(req.offset || 0),
        hidebroken: "true",
        order: "clickcount",
        reverse: "true",
      });
      for (const key of ["name", "country", "language", "tag"])
        if (req[key]) params.set(key, req[key]);
      radioSearch(params)
        .then((list) => {
          const rows = [];
          for (const s of list) {
            let url;
            try {
              url = new URL(s.url_resolved || s.url);
            } catch {
              continue;
            }
            if (
              url.protocol !== "https:" ||
              url.username ||
              url.password ||
              (url.port && url.port !== "443") ||
              !s.stationuuid
            )
              continue;
            const station = {
              id: "radio:" + s.stationuuid,
              title: s.name,
              genre: s.tags,
              provider: s.country,
              url: url.href,
              favicon: s.favicon,
              homepage: s.homepage,
              codec: s.codec,
              bitRate: s.bitrate,
              hls: Boolean(s.hls),
              saved: savedRadio.has("radio:" + s.stationuuid),
            };
            if (!stations.some((s) => s.id === station.id))
              stations.push(station);
            rows.push(station);
          }
          deliver({ type: "response", id: req.id, data: { stations: rows } });
        })
        .catch(() =>
          deliver({
            type: "response",
            id: req.id,
            error:
              "Radio directory is unavailable. Try again, or use the starter stations.",
          }),
        );
      return;
    }
    switch (req.action) {
      case "ready":
        deliver({ type: "catalog", data: { albums } });
        emit();
        break;
      case "tracks":
        data.albumStarred = Boolean(
          albums.find((a) => a.id === req.value)?.starred,
        );
        data.tracks = all
          .filter((t) =>
            req.mode === "all"
              ? true
              : req.mode === "album"
                ? t.albumId === req.value
                : req.mode === "favourites"
                  ? favourites.has(t.id)
                  : req.mode === "playlist"
                    ? (
                        playlists.find((p) => p.id === req.value)?.tracks || []
                      ).includes(t.id)
                    : (t.title + " " + t.album + " " + t.artist)
                        .toLowerCase()
                        .includes(req.value.toLowerCase()),
          )
          .map((t) => ({ ...t, favourite: favourites.has(t.id) }));
        if (req.mode === "all")
          data.tracks = data.tracks.slice(
            Number(req.value) || 0,
            (Number(req.value) || 0) + 200,
          );
        break;
      case "trackInfo":
        data.track = all.find((t) => t.id === req.track);
        break;
      case "radioSaved":
        data.stations = [...savedRadio.values()].map((s) => ({
          ...s,
          saved: true,
        }));
        break;
      case "radioSave": {
        const s = stations.find((s) => s.id === req.station);
        if (!s) throw Error("Station unavailable");
        req.save
          ? savedRadio.set(s.id, { ...s, saved: true })
          : savedRadio.delete(s.id);
        localStorage.setItem(
          "naviglass-saved-radio",
          JSON.stringify([...savedRadio.values()]),
        );
        data.saved = Boolean(req.save);
        break;
      }
      case "radioStations":
        data.stations = stations
          .slice(0, 4)
          .map((s) => ({ ...s, saved: savedRadio.has(s.id) }));
        break;
      case "radioPlay":
        radio(req.station, req.ids);
        break;
      case "play":
        stopRadio();
        queue = req.ids;
        albumOrder = req.albumIds || [];
        order = buildPlaybackOrder(queue.length, req.index, state.shuffle);
        startSong(req.index);
        emit();
        break;
      case "toggle":
        if (live) {
          if (!audio.paused || state.buffering) audio.pause();
          else {
            state.error = "";
            state.buffering = true;
            audio.play().catch(failed);
          }
        } else state.playing = !state.playing;
        emit();
        break;
      case "next":
      case "previous": {
        const direction = req.action === "next" ? 1 : -1;
        if (live) {
          const id = queue[queue.indexOf(state.track?.id) + direction];
          if (id) radio(id, queue);
        } else advance(direction);
        break;
      }
      case "seek":
      case "volume":
        if (req.action === "volume") {
          audio.volume = req.value;
          state.volume = req.value;
          emit();
          break;
        }
        state.position = req.position;
        emit();
        break;
      case "shuffle":
        state.shuffle = !state.shuffle;
        order = buildPlaybackOrder(
          queue.length,
          queue.indexOf(state.track?.id),
          state.shuffle,
        );
        emit();
        break;
      case "repeat":
        state.repeat = state.repeat === 0 ? 2 : state.repeat === 2 ? 1 : 0;
        emit();
        break;
      case "albumFavourite": {
        const album = albums.find((a) => a.id === req.album);
        if (!album) throw new Error("Album unavailable");
        album.starred = !album.starred;
        data.albumStarred = album.starred;
        break;
      }
      case "favourite":
        favourites.has(req.track)
          ? favourites.delete(req.track)
          : favourites.add(req.track);
        data.favourite = favourites.has(req.track);
        emit();
        break;
      case "playlists":
      case "serverStatus":
        if (req.action === "serverStatus") {
          data = { connected: false, active: false };
          break;
        }
        data.playlists = playlists.map((p) => ({
          ...p,
          count: p.tracks.length,
        }));
        break;
      case "createPlaylist":
        data.playlist = "p" + playlists.length;
        playlists.push({ id: data.playlist, name: req.name, tracks: [] });
        break;
      case "addPlaylist":
        playlists.find((p) => p.id === req.playlist)?.tracks.push(req.track);
        break;
      default:
        deliver({
          type: "error",
          error:
            "Design preview only. Source selection works inside the Android app.",
        });
    }
    queueMicrotask(() => deliver({ type: "response", id: req.id, data }));
  },
};
setInterval(() => {
  if (state.playing && !live) {
    state.position = Math.min(state.duration, state.position + 500);
    if (state.position >= state.duration) advance(1, true);
    else emit();
  }
}, 500);
