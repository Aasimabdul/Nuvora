import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  AudioLines, CircleHelp, Compass, Disc3, Heart, Home, Library, ListMusic,
  ListPlus, LoaderCircle, Music2, Pause, Play, Radio, Search, Settings2,
  SkipBack, SkipForward, Volume2, VolumeX, X, ExternalLink
} from "lucide-react";
import { audiusStreamUrl, artworkUrl, formatDuration, getTrendingAudius, searchAudius, type AudiusTrack } from "./providers/audius";

type Section = "Home" | "Search" | "Discover" | "Library" | "Settings";
const NAV: { label: Section; icon: typeof Home }[] = [
  { label: "Home", icon: Home }, { label: "Search", icon: Search },
  { label: "Discover", icon: Compass }, { label: "Library", icon: Library },
  { label: "Settings", icon: Settings2 }
];
const safeRead = <T,>(key: string, fallback: T): T => {
  try { const value = localStorage.getItem(key); return value ? JSON.parse(value) as T : fallback; }
  catch { return fallback; }
};

export default function App() {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const searchTimer = useRef<number | undefined>(undefined);
  const requestRef = useRef<AbortController | null>(null);
  const [section, setSection] = useState<Section>("Home");
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<AudiusTrack[]>([]);
  const [trending, setTrending] = useState<AudiusTrack[]>([]);
  const [loading, setLoading] = useState(false);
  const [homeLoading, setHomeLoading] = useState(true);
  const [error, setError] = useState("");
  const [homeError, setHomeError] = useState("");
  const [current, setCurrent] = useState<AudiusTrack | null>(() => safeRead<AudiusTrack | null>("nuvora.desktop.current", null));
  const [playing, setPlaying] = useState(false);
  const [buffering, setBuffering] = useState(false);
  const [playError, setPlayError] = useState("");
  const [position, setPosition] = useState(0);
  const [duration, setDuration] = useState(0);
  const [volume, setVolume] = useState(() => safeRead("nuvora.desktop.volume", 0.8));
  const [muted, setMuted] = useState(false);
  const [queue, setQueue] = useState<AudiusTrack[]>(() => safeRead("nuvora.desktop.queue", []));
  const [favorites, setFavorites] = useState<AudiusTrack[]>(() => safeRead("nuvora.desktop.favorites", []));
  const [recent, setRecent] = useState<AudiusTrack[]>(() => safeRead("nuvora.desktop.recent", []));
  const [queueOpen, setQueueOpen] = useState(false);
  const [status, setStatus] = useState("Connecting to Audius…");

  useEffect(() => {
    const controller = new AbortController();
    getTrendingAudius(controller.signal).then((tracks) => {
      setTrending(tracks);
      setStatus("Audius catalogue connected");
    }).catch((e: unknown) => {
      if (!(e instanceof DOMException && e.name === "AbortError")) {
        setHomeError(e instanceof Error ? e.message : "Unable to load Audius discovery.");
        setStatus("Audius unavailable");
      }
    }).finally(() => setHomeLoading(false));
    return () => controller.abort();
  }, []);

  useEffect(() => { localStorage.setItem("nuvora.desktop.current", JSON.stringify(current)); }, [current]);
  useEffect(() => { localStorage.setItem("nuvora.desktop.queue", JSON.stringify(queue.slice(0, 100))); }, [queue]);
  useEffect(() => { localStorage.setItem("nuvora.desktop.favorites", JSON.stringify(favorites.slice(0, 500))); }, [favorites]);
  useEffect(() => { localStorage.setItem("nuvora.desktop.recent", JSON.stringify(recent.slice(0, 100))); }, [recent]);
  useEffect(() => { localStorage.setItem("nuvora.desktop.volume", JSON.stringify(volume)); }, [volume]);

  useEffect(() => {
    const controller = new AbortController();
    requestRef.current?.abort();
    if (searchTimer.current) window.clearTimeout(searchTimer.current);
    if (!query.trim()) {
      setResults([]);
      setError("");
      setLoading(false);
      return () => controller.abort();
    }
    setLoading(true);
    setError("");
    searchTimer.current = window.setTimeout(() => {
      requestRef.current = controller;
      searchAudius(query, controller.signal).then(setResults).catch((e: unknown) => {
        if (!(e instanceof DOMException && e.name === "AbortError")) {
          setError(e instanceof Error ? e.message : "Search failed. Try again.");
          setResults([]);
        }
      }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    }, 320);
    return () => { window.clearTimeout(searchTimer.current); controller.abort(); };
  }, [query]);

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;
    audio.volume = volume;
    audio.muted = muted;
  }, [volume, muted]);

  const activeList = useMemo(() => section === "Library" ? favorites : trending, [section, favorites, trending]);

  const playTrack = useCallback(async (track: AudiusTrack) => {
    const audio = audioRef.current;
    if (!audio) return;
    setPlayError("");
    setBuffering(true);
    setCurrent(track);
    setPosition(0);
    setDuration(track.duration ?? 0);
    try {
      audio.pause();
      audio.src = audiusStreamUrl(track.id);
      audio.load();
      await audio.play();
      setPlaying(true);
      setRecent((old) => [track, ...old.filter((item) => item.id !== track.id)].slice(0, 100));
      setStatus("Playing Audius stream");
    } catch (e) {
      setPlaying(false);
      setBuffering(false);
      setPlayError(e instanceof Error ? e.message : "This track could not be played.");
      setStatus("Playback unavailable");
    }
  }, []);

  const togglePlayback = async () => {
    const audio = audioRef.current;
    if (!audio) return;
    if (audio.paused) {
      if (!current) {
        const first = queue[0] ?? trending[0];
        if (first) await playTrack(first);
        else setPlayError("Search for a track or wait for discovery to load.");
        return;
      }
      try { await audio.play(); setPlaying(true); setPlayError(""); }
      catch (e) { setPlaying(false); setPlayError(e instanceof Error ? e.message : "Playback was blocked."); }
    } else { audio.pause(); setPlaying(false); }
  };

  const addQueue = (track: AudiusTrack) => setQueue((old) => old.some((item) => item.id === track.id) ? old : [...old, track].slice(0, 100));
  const toggleFavorite = (track: AudiusTrack) => setFavorites((old) => old.some((item) => item.id === track.id) ? old.filter((item) => item.id !== track.id) : [track, ...old].slice(0, 500));
  const isFavorite = (track: AudiusTrack) => favorites.some((item) => item.id === track.id);
  const playNext = async () => {
    const next = queue[0];
    if (!next) { setPlayError("Your queue is empty."); return; }
    setQueue((old) => old.slice(1));
    await playTrack(next);
  };
  const clearSearch = () => { setQuery(""); setResults([]); setError(""); };
  const activeList = useMemo(() => section === "Library" ? favorites : trending, [section, favorites, trending]);

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#" onClick={(e) => { e.preventDefault(); setSection("Home"); }} aria-label="Nuvora home"><span className="brand-mark"><AudioLines size={25} strokeWidth={2.3} /></span><span className="brand-word">nu<span>vora</span><small>DESKTOP</small></span></a>
        <div className="side-label">WORKSPACE</div>
        <nav className="nav-list" aria-label="Main navigation">{NAV.map(({ label, icon: Icon }) => <button key={label} className={section === label ? "nav-item active" : "nav-item"} onClick={() => setSection(label)} aria-current={section === label ? "page" : undefined}><Icon size={18} /><span>{label}</span>{label === "Library" && favorites.length > 0 && <span className="nav-count">{favorites.length}</span>}</button>)}</nav>
        <div className="sidebar-spacer" />
        <div className="provider-card"><span className="provider-orb"><Radio size={18} /></span><div><strong>Audius</strong><span>Public music catalog</span></div><span className={homeError ? "connection-dot offline" : "connection-dot"} title={homeError ? "Disconnected" : "Connection status"} /></div>
        <div className="sidebar-foot"><span className="tiny-wave"><AudioLines size={15} /></span> Music, in your own orbit.</div>
      </aside>
      <main className="main-area">
        <header className="topbar"><div className="breadcrumb"><span>NUVORA</span><span className="crumb-slash">/</span><strong>{section}</strong></div><div className="topbar-right"><span className="status-indicator" /><span>{status}</span><span className="version-pill">DESKTOP 1.0.0</span></div></header>
        <div className="content-scroll">
          {section === "Home" && <section className="home-view">
            <div className="hero"><div className="hero-copy"><div className="eyebrow"><span className="eyebrow-line" /> YOUR SOUND, YOUR SPACE</div><h1>Find your<br /><span>next frequency.</span></h1><p>Discover independent sounds and let the music take you somewhere new.</p><button className="primary-button" onClick={() => setSection("Discover")}><Compass size={17} /> Explore music <span>↗</span></button></div><div className="hero-art" aria-hidden="true"><div className="orbit orbit-one" /><div className="orbit orbit-two" /><div className="orbit orbit-three" /><div className="hero-disc"><div className="disc-groove" /><div className="disc-center"><AudioLines size={31} /></div></div><div className="floating-note note-one">♪</div><div className="floating-note note-two">♫</div><div className="sound-bars"><i /><i /><i /><i /><i /><i /><i /><i /><i /></div></div><div className="hero-index"><span>01</span><span className="index-line" /><span>DISCOVER</span></div></div>
            <div className="section-heading"><div><div className="eyebrow">FRESH FROM THE FEED</div><h2>Trending on Audius</h2></div><button className="text-button" onClick={() => setSection("Discover")}>View all <span>↗</span></button></div>
            {homeError && <div className="notice error-notice"><CircleHelp size={18} /><div><strong>Audius could not be reached</strong><span>{homeError}</span></div><button onClick={() => { setHomeLoading(true); setHomeError(""); getTrendingAudius().then(setTrending).catch(e => setHomeError(e instanceof Error ? e.message : "Request failed")).finally(() => setHomeLoading(false)); }}>Retry</button></div>}
            {homeLoading ? <div className="loading-row"><LoaderCircle className="spin" size={22} /> Loading public catalog…</div> : <TrackGrid tracks={trending.slice(0, 8)} current={current} playing={playing} favorites={favorites} onPlay={playTrack} onFavorite={toggleFavorite} onQueue={addQueue} />}
            <div className="section-heading recent-heading"><div><div className="eyebrow">PICK UP WHERE YOU LEFT OFF</div><h2>Recently played</h2></div></div>
            {recent.length ? <TrackGrid tracks={recent.slice(0, 4)} current={current} playing={playing} favorites={favorites} onPlay={playTrack} onFavorite={toggleFavorite} onQueue={addQueue} /> : <div className="empty-strip"><Disc3 size={23} /><div><strong>Your listening history starts here</strong><span>Play a track and it will show up here.</span></div></div>}
          </section>}
          {section === "Search" && <section className="standard-view"><div className="page-intro"><div className="eyebrow">LOOK A LITTLE CLOSER</div><h1>Search <span>your sound.</span></h1><p>Search public Audius tracks. Playback availability depends on the source response.</p></div><label className="search-box"><Search size={21} /><input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Tracks, artists, moods…" autoFocus aria-label="Search Audius tracks" />{query && <button aria-label="Clear search" onClick={clearSearch}><X size={17} /></button>}<kbd>ENTER</kbd></label><div className="results-meta"><span>{query.trim() ? "AUDIOUS RESULTS" : "START WITH A SEARCH"}</span>{query.trim() && <span>{loading ? "Searching…" : `${results.length} tracks`}</span>}</div>{loading && <div className="loading-row"><LoaderCircle className="spin" size={22} /> Searching Audius…</div>}{error && <div className="notice error-notice"><CircleHelp size={18} /><div><strong>Search failed</strong><span>{error}</span></div></div>}{!loading && !error && query.trim() && results.length === 0 && <div className="empty-state"><Search size={28} /><h3>No tracks found</h3><p>Try a different title, artist or search term.</p></div>}{!query.trim() && <div className="empty-state"><AudioLines size={28} /><h3>What are you in the mood for?</h3><p>Search Audius to find public tracks and independent artists.</p></div>}{results.length > 0 && <TrackRows tracks={results} current={current} playing={playing} favorites={favorites} onPlay={playTrack} onFavorite={toggleFavorite} onQueue={addQueue} />}</section>}
          {section === "Discover" && <section className="standard-view"><div className="page-intro"><div className="eyebrow">TUNE INTO SOMETHING NEW</div><h1>Discover <span>the current.</span></h1><p>Browse public music available through the Audius catalog.</p></div><div className="discover-banner"><div><Radio size={22} /><span>LIVE CATALOG</span></div><h2>Independent sounds.<br /><span>Open discovery.</span></h2><p>Trending public tracks, delivered from Audius when the service is reachable.</p><button className="primary-button" onClick={() => setSection("Search")}><Search size={16} /> Search catalog</button></div><div className="section-heading"><div><div className="eyebrow">POPULAR RIGHT NOW</div><h2>Trending tracks</h2></div><button className="text-button" onClick={() => { setHomeLoading(true); getTrendingAudius().then(setTrending).catch(e => setHomeError(e instanceof Error ? e.message : "Request failed")).finally(() => setHomeLoading(false)); }}>Refresh ↻</button></div>{homeLoading ? <div className="loading-row"><LoaderCircle className="spin" size={22} /> Loading catalog…</div> : <TrackGrid tracks={trending} current={current} playing={playing} favorites={favorites} onPlay={playTrack} onFavorite={toggleFavorite} onQueue={addQueue} />}{homeError && <div className="notice error-notice"><CircleHelp size={18} /><div><strong>Catalog unavailable</strong><span>{homeError}</span></div></div>}</section>}
          {section === "Library" && <section className="standard-view"><div className="page-intro"><div className="eyebrow">YOUR PERSONAL COLLECTION</div><h1>Your <span>library.</span></h1><p>Favorites and listening history are stored locally on this device.</p></div><div className="library-tabs"><span className="library-tab selected"><Heart size={15} /> Favorites <b>{favorites.length}</b></span><button className="library-tab" onClick={() => setSection("Home")}><Disc3 size={15} /> Recent <b>{recent.length}</b></button><button className="library-tab" onClick={() => setQueueOpen(true)}><ListMusic size={15} /> Queue <b>{queue.length}</b></button></div>{favorites.length ? <TrackRows tracks={favorites} current={current} playing={playing} favorites={favorites} onPlay={playTrack} onFavorite={toggleFavorite} onQueue={addQueue} /> : <div className="empty-state"><Heart size={28} /><h3>Keep the tracks you love</h3><p>Tap the heart on any track to save it here.</p><button className="primary-button" onClick={() => setSection("Discover")}>Find music <span>↗</span></button></div>}</section>}
          {section === "Settings" && <section className="standard-view"><div className="page-intro"><div className="eyebrow">MAKE IT YOURS</div><h1>App <span>settings.</span></h1><p>Lightweight preferences and honest provider status.</p></div><div className="settings-card"><div className="settings-icon"><Volume2 size={19} /></div><div className="settings-copy"><strong>Playback volume</strong><span>Saved on this device</span></div><input aria-label="Playback volume" className="settings-range" type="range" min="0" max="1" step="0.01" value={volume} onChange={(e) => setVolume(Number(e.target.value))} /><b className="settings-value">{Math.round(volume * 100)}%</b></div><div className="settings-card"><div className="settings-icon"><Radio size={19} /></div><div className="settings-copy"><strong>Audius public catalog</strong><span>{homeError ? "Unavailable — check your connection" : "Discovery and stream URLs requested when needed"}</span></div><span className={homeError ? "provider-status bad" : "provider-status"}>{homeError ? "OFFLINE" : "CONNECTED"}</span></div><div className="settings-card"><div className="settings-icon"><ExternalLink size={19} /></div><div className="settings-copy"><strong>Other music services</strong><span>Spotify, YouTube Music and JioSaavn are not configured as desktop playback providers yet.</span></div><span className="provider-status neutral">UNAVAILABLE</span></div><div className="settings-card"><div className="settings-icon"><Library size={19} /></div><div className="settings-copy"><strong>Clear local library data</strong><span>Removes favorites, queue and recent history from this app.</span></div><button className="outline-button" onClick={() => { if (window.confirm("Clear favorites, queue and recent history on this device?")) { setFavorites([]); setQueue([]); setRecent([]); } }}>Clear data</button></div><p className="settings-note">No provider passwords or access tokens are stored by this initial desktop build.</p></section>}
        </div>
      </main>
      <footer className="player-bar"><div className="now-playing">{current ? <Artwork track={current} large /> : <div className="cover-placeholder track-art-large"><Music2 size={22} /></div>}<div className="now-meta"><strong title={current?.title ?? "Nothing playing"}>{current?.title ?? "Nothing playing"}</strong><span>{current?.user?.name ?? "Choose a track to begin"}</span></div>{current && <button className={isFavorite(current) ? "icon-button favorited" : "icon-button"} aria-label={isFavorite(current) ? "Remove from favorites" : "Add to favorites"} onClick={() => toggleFavorite(current)}><Heart size={17} fill={isFavorite(current) ? "currentColor" : "none"} /></button>}</div>
        <div className="player-center"><div className="player-controls"><button className="icon-button" aria-label="Previous track" onClick={() => { const i = current ? recent.findIndex(t => t.id === current.id) : -1; const prev = recent[i + 1]; if (prev) void playTrack(prev); else setPlayError("No previous track in listening history."); }}><SkipBack size={17} /></button><button className="play-button" aria-label={playing ? "Pause" : "Play"} onClick={() => void togglePlayback()}>{buffering ? <LoaderCircle size={19} className="spin" /> : playing ? <Pause size={19} fill="currentColor" /> : <Play size={19} fill="currentColor" />}</button><button className="icon-button" aria-label="Play next queued track" onClick={() => void playNext()}><SkipForward size={17} /></button></div><div className="timeline"><span>{formatDuration(position)}</span><input aria-label="Seek track" type="range" min="0" max={Math.max(duration, 1)} step="1" value={Math.min(position, Math.max(duration, 1))} onChange={(e) => { const audio = audioRef.current; if (audio && Number.isFinite(audio.duration) && audio.duration > 0) audio.currentTime = Number(e.target.value); }} disabled={!duration} /><span>{formatDuration(duration)}</span></div>{playError && <div className="player-error" role="status">{playError}</div>}</div>
        <div className="player-right"><button className="icon-button queue-trigger" aria-label="Open queue" onClick={() => setQueueOpen(v => !v)}><ListMusic size={19} /><span>{queue.length}</span></button><button className="icon-button" aria-label={muted ? "Unmute" : "Mute"} onClick={() => setMuted(v => !v)}>{muted ? <VolumeX size={18} /> : <Volume2 size={18} />}</button><input aria-label="Volume" className="volume-range" type="range" min="0" max="1" step="0.01" value={volume} onChange={(e) => setVolume(Number(e.target.value))} /></div></footer>
      {queueOpen && <div className="queue-backdrop" onClick={() => setQueueOpen(false)}><section className="queue-panel" onClick={(e) => e.stopPropagation()} aria-label="Playback queue"><div className="queue-header"><div><span className="eyebrow">UP NEXT</span><h2>Queue <span>{queue.length}</span></h2></div><button className="icon-button" aria-label="Close queue" onClick={() => setQueueOpen(false)}><X size={19} /></button></div>{current && <div className="queue-current"><Artwork track={current} /><div><small>NOW PLAYING</small><strong>{current.title}</strong><span>{current.user?.name ?? "Audius"}</span></div></div>}{queue.length ? queue.map((track, index) => <div className="queue-row" key={track.id}><span className="queue-number">{String(index + 1).padStart(2, "0")}</span><Artwork track={track} /><div className="queue-track"><strong>{track.title}</strong><span>{track.user?.name ?? "Audius"}</span></div><button className="icon-button" aria-label={`Play ${track.title}`} onClick={() => { setQueue(old => old.filter(t => t.id !== track.id)); void playTrack(track); }}><Play size={15} /></button><button className="icon-button" aria-label={`Remove ${track.title} from queue`} onClick={() => setQueue(old => old.filter(t => t.id !== track.id))}><X size={15} /></button></div>) : <div className="queue-empty"><ListMusic size={27} /><p>Your queue is empty.</p><span>Add tracks with the + button.</span></div>}<button className="outline-button queue-clear" disabled={!queue.length} onClick={() => setQueue([])}>Clear queue</button></section></div>}
      <audio ref={audioRef} preload="none" onLoadStart={() => setBuffering(true)} onCanPlay={() => setBuffering(false)} onPlaying={() => { setPlaying(true); setBuffering(false); }} onPause={() => setPlaying(false)} onTimeUpdate={(e) => { setPosition(e.currentTarget.currentTime); if (Number.isFinite(e.currentTarget.duration)) setDuration(e.currentTarget.duration); }} onDurationChange={(e) => { if (Number.isFinite(e.currentTarget.duration)) setDuration(e.currentTarget.duration); }} onWaiting={() => setBuffering(true)} onStalled={() => { setBuffering(true); setPlayError("The stream stalled. Check your connection or try another track."); }} onError={() => { setPlaying(false); setBuffering(false); setPlayError("This stream could not be loaded. It may be unavailable or unsupported."); }} onEnded={() => { setPlaying(false); void playNext(); }} />
    </div>
  );
}

function Artwork({ track, large = false }: { track: AudiusTrack; large?: boolean }) {
  const [failed, setFailed] = useState(false);
  const src = artworkUrl(track);
  return <div className={large ? "track-art track-art-large" : "track-art"}>{src && !failed ? <img src={src} alt="" loading="lazy" onError={() => setFailed(true)} /> : <div className="cover-placeholder"><Music2 size={large ? 22 : 17} /></div>}</div>;
}

function TrackGrid({ tracks, current, playing, favorites, onPlay, onFavorite, onQueue }: {
  tracks: AudiusTrack[]; current: AudiusTrack | null; playing: boolean; favorites: AudiusTrack[];
  onPlay: (track: AudiusTrack) => void; onFavorite: (track: AudiusTrack) => void; onQueue: (track: AudiusTrack) => void;
}) {
  if (!tracks.length) return <div className="empty-strip"><Disc3 size={23} /><div><strong>No tracks to show right now</strong><span>Try again when the catalog is available.</span></div></div>;
  return <div className="track-grid">{tracks.map((track, index) => <article className="track-card" key={track.id}><button className="card-art-button" aria-label={`Play ${track.title}`} onClick={() => onPlay(track)}><Artwork track={track} /><span className="card-play">{current?.id === track.id && playing ? <AudioLines size={20} /> : <Play size={19} fill="currentColor" />}</span><span className="card-index">{String(index + 1).padStart(2, "0")}</span></button><div className="card-info"><button className="track-title" onClick={() => onPlay(track)} title={track.title}>{track.title}</button><span>{track.user?.name ?? "Independent artist"}</span></div><div className="card-actions"><button className={favorites.some(t => t.id === track.id) ? "icon-button favorited" : "icon-button"} aria-label={favorites.some(t => t.id === track.id) ? "Remove favorite" : "Add favorite"} onClick={() => onFavorite(track)}><Heart size={16} fill={favorites.some(t => t.id === track.id) ? "currentColor" : "none"} /></button><button className="icon-button" aria-label="Add to queue" onClick={() => onQueue(track)}><ListPlus size={17} /></button><span className="track-duration">{formatDuration(track.duration)}</span></div></article>)}</div>;
}

function TrackRows({ tracks, current, playing, favorites, onPlay, onFavorite, onQueue }: {
  tracks: AudiusTrack[]; current: AudiusTrack | null; playing: boolean; favorites: AudiusTrack[];
  onPlay: (track: AudiusTrack) => void; onFavorite: (track: AudiusTrack) => void; onQueue: (track: AudiusTrack) => void;
}) {
  return <div className="track-table"><div className="table-head"><span>#</span><span>TRACK</span><span>LENGTH</span><span>ACTIONS</span></div>{tracks.map((track, index) => <div className={current?.id === track.id && playing ? "table-row is-playing" : "table-row"} key={track.id}><span className="row-index">{current?.id === track.id && playing ? <AudioLines size={15} /> : String(index + 1).padStart(2, "0")}</span><button className="row-track" onClick={() => onPlay(track)}><Artwork track={track} /><span><strong>{track.title}</strong><small>{track.user?.name ?? "Independent artist"}</small></span></button><span className="row-duration">{formatDuration(track.duration)}</span><div className="row-actions"><button className="icon-button" aria-label="Play track" onClick={() => onPlay(track)}>{current?.id === track.id && playing ? <Pause size={16} /> : <Play size={16} />}</button><button className={favorites.some(t => t.id === track.id) ? "icon-button favorited" : "icon-button"} aria-label="Toggle favorite" onClick={() => onFavorite(track)}><Heart size={16} fill={favorites.some(t => t.id === track.id) ? "currentColor" : "none"} /></button><button className="icon-button" aria-label="Add to queue" onClick={() => onQueue(track)}><ListPlus size={17} /></button></div></div>)}</div>;
}
