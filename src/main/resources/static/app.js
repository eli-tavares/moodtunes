import { lineChart, stackedBarChart, tableFor, niceCountAxis } from './charts.js';

const TOKEN_KEY = 'moodtunes.accessToken';
const REQUEST_TIMEOUT_MS = 45000;
const CHECK_TIMEOUT_MS = 10000;
const WAKE_ATTEMPTS = 4;          // a sleeping host can answer 502 for a few seconds while it boots
const WAKE_DELAY_MS = 2500;
const MAX_EMOTIONS = 3;

const $ = (id) => document.getElementById(id);
const bootEl = $('boot');
const lockCard = $('lock');
const lockForm = $('lockForm');
const tokenInput = $('tokenInput');
const rememberChk = $('rememberChk');
const unlockBtn = $('unlockBtn');
const lockStatus = $('lockStatus');
const lockBtn = $('lockBtn');
const appContent = $('appContent');
const moodText = $('moodText');
const analyzeBtn = $('analyzeBtn');
const emotionsCard = $('emotionsCard');
const emotionsContainer = $('emotionsContainer');
const saveBtn = $('saveBtn');
const keepWords = $('keepWords');
const flagNote = $('flagNote');
const newBtn = $('newBtn');
const newBtn2 = $('newBtn2');
const musicBtn = $('musicBtn');
const whoami = $('whoami');
const mediaCard = $('mediaCard');
const mediaQuestion = $('mediaQuestion');
const songsBtn = $('songsBtn');
const albumsBtn = $('albumsBtn');
const resultsCard = $('resultsCard');
const resultsList = $('resultsList');
const statusEl = $('status');
const tabCheckin = $('tabCheckin');
const tabReports = $('tabReports');
const viewCheckin = $('viewCheckin');
const viewReports = $('viewReports');
const reportBody = $('reportBody');
const reportStatus = $('reportStatus');

let selected = [];            // emotion codes picked for this check-in, in the order tapped (max 3)
let lastSavedKey = null;      // the selection that was last saved, so one tap cannot save the same thing twice
// Short plain-language meanings, shown when a word is tapped, to help choose between close words.
const EMOTION_HELP = {
    happy: 'Glad, light, enjoying the moment.',
    excited: 'Energised and looking forward to something.',
    proud: 'Pleased with something you did or who you are.',
    grateful: 'Thankful for someone or something.',
    loved: 'Cared for and valued by someone.',
    inspired: 'Moved to create, try or do something.',
    hopeful: 'Believing things can get better.',
    relieved: 'A weight has lifted; the worry is over.',
    calm: 'Settled and unhurried inside.',
    content: 'Satisfied; nothing is missing right now.',
    relaxed: 'Loose and at ease, without tension.',
    curious: 'Interested and wanting to find out more.',
    motivated: 'Ready and willing to get on with things.',
    neutral: 'Neither good nor bad; just ordinary.',
    nostalgic: 'Missing a time or person from the past, often warmly.',
    reflective: 'Thinking things over, looking inward.',
    surprised: 'Caught off guard, for better or worse.',
    anxious: 'Worried or uneasy, often about what might happen.',
    stressed: 'Under pressure, as if there is too much to handle.',
    overwhelmed: 'Too much at once to cope with or sort out.',
    sad: 'Low or heavy, often after a loss or hurt.',
    lonely: 'Missing connection, even if people are around.',
    angry: 'Strong displeasure, often at something unfair.',
    frustrated: 'Blocked or stuck, things are not working.',
    irritable: 'Short-tempered; small things get under your skin.',
    disappointed: 'Let down; things did not turn out as hoped.',
    guilty: 'Feeling you did something wrong or let someone down.',
    bored: 'Under-stimulated; nothing holds your interest.',
    tired: 'Low on energy, body or mind.',
    hungry: 'Needing food.',
    sleepy: 'Ready to sleep.',
    restless: 'Unable to settle; fidgety.',
    sore: 'Aching or hurting in the body.',
    unwell: 'Physically off, ill or run down.',
};
const fmtStrength = (v) => (v === null || v === undefined ? '–' : v.toFixed(1));
const STRENGTH_NAME = ['', 'barely', 'mild', 'moderate', 'strong', 'very strong'];
let summaryData = null;                         // the last-7-days summary on screen (cleared on lock)
let strength = null;                            // 1-5, or null when not chosen (optional)
let analyzed = { text: '', flagged: false };   // the sentence behind the emotions on screen (kept only if the box is ticked)
let me = { name: '', musicEnabled: true };   // who is signed in and their settings (from /api/me)
let seenVersion = null;       // the server version this page started with (X-App-Version)
let memoryToken = '';         // fallback when browser storage is unavailable (private mode, blocked storage)

// ---- new deployment detection ---------------------------------------------------------------------------
// An installed app can sit open on an old copy of the page while the server is redeployed. When the server's
// version changes under us, offer a reload instead of failing with confusing errors.
function noteVersion(resp) {
    const version = resp.headers.get('X-App-Version');
    if (!version) return;
    if (seenVersion === null) seenVersion = version;
    else if (version !== seenVersion) $('updateBar').hidden = false;
}
$('reloadBtn').addEventListener('click', () => window.location.reload());

// ---- access code storage ---------------------------------------------------------------------------------
// localStorage = "keep me unlocked on this device"; sessionStorage = until the app is closed.
function readToken() {
    if (memoryToken) return memoryToken;
    try { return sessionStorage.getItem(TOKEN_KEY) || localStorage.getItem(TOKEN_KEY) || ''; } catch { return ''; }
}
function clearToken() {
    memoryToken = '';
    try { localStorage.removeItem(TOKEN_KEY); sessionStorage.removeItem(TOKEN_KEY); } catch { /* ignore */ }
}
function saveToken(value, remember) {
    clearToken();
    memoryToken = value;
    try { (remember ? localStorage : sessionStorage).setItem(TOKEN_KEY, value); } catch { /* memory only */ }
}

// ---- views -----------------------------------------------------------------------------------------------
function showView(view) {
    bootEl.hidden = view !== 'boot';
    lockCard.hidden = view !== 'lock';
    appContent.hidden = view !== 'app';
}

// Clears the check-in flow so another one can start (after saving, or to abandon this one).
function resetFlow() {
    moodText.value = '';
    emotionsContainer.replaceChildren();
    resultsList.replaceChildren();
    emotionsCard.hidden = true;
    mediaCard.hidden = true;
    resultsCard.hidden = true;
    selected = [];
    lastSavedKey = null;
    analyzed = { text: '', flagged: false };
    strength = null;
    $('emotionHint').textContent = 'Tap a word to see what it means.';
    keepWords.checked = false;
    flagNote.hidden = true;
    setStatus('');
    updateSelection();
}

function newCheckIn() {
    resetFlow();
    window.scrollTo({ top: 0, behavior: 'smooth' });
    moodText.focus();
}

function resetApp() {
    resetFlow();
    me = { name: '', musicEnabled: true };
    clearReports();
    setTab('checkin');
}

function showLock(message = '') {
    resetApp();                       // nothing from a previous session stays in the page while locked
    tokenInput.value = '';
    lockStatus.textContent = message;
    showView('lock');
    tokenInput.focus();
}

function showApp(signedIn) {
    lockStatus.textContent = '';
    if (signedIn) applyMe(signedIn);
    showView('app');
}

// Shows who is signed in and keeps the music button and the music cards in step with their setting.
function applyMe(next) {
    me = next;
    const on = me.musicEnabled;
    musicBtn.setAttribute('aria-pressed', String(on));
    musicBtn.textContent = on ? '🎵 Music: on' : '🎵 Music: off';
    musicBtn.setAttribute('aria-label', on ? 'Music suggestions are on. Tap to turn them off.'
        : 'Music suggestions are off. Tap to turn them on.');
    whoami.textContent = (me.name && me.name !== 'local' && me.name !== 'me' ? `Hi, ${me.name}. ` : '')
        + (on ? 'Tell me how you feel, save a check-in, and get music that fits.'
            : 'Tell me how you feel and save a check-in.');
    if (!on) resultsCard.hidden = true;
    updateSelection();
}

async function toggleMusic() {
    musicBtn.disabled = true;
    try {
        applyMe(await request('PUT', '/api/me/music', { enabled: !me.musicEnabled }));
    } catch (e) {
        handleAuthOr(e, (msg) => setStatus(msg, true));
    } finally {
        musicBtn.disabled = false;
    }
}

// ---- access check ----------------------------------------------------------------------------------------
// -> { result: 'ok' | 'denied' | 'throttled' | 'offline' | 'error', me }
async function checkAccess(token) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), CHECK_TIMEOUT_MS);
    try {
        const resp = await fetch('/api/me', {
            headers: token ? { 'X-Access-Token': token } : {},
            cache: 'no-store',
            signal: controller.signal,
        });
        noteVersion(resp);
        if (resp.ok) {
            let signedIn = null;
            try { signedIn = await resp.json(); } catch { /* keep defaults */ }
            return { result: 'ok', me: signedIn };
        }
        if (resp.status === 401) return { result: 'denied' };
        if (resp.status === 429) return { result: 'throttled' };
        return { result: 'error' };
    } catch {
        return { result: 'offline' };
    } finally {
        clearTimeout(timer);
    }
}

// Retries 5xx answers (a sleeping/booting host), so this first call also wakes the server for the real ones.
async function checkAccessWithWake(token, onWaiting) {
    let outcome = { result: 'error' };
    for (let attempt = 0; attempt < WAKE_ATTEMPTS; attempt++) {
        outcome = await checkAccess(token);
        if (outcome.result !== 'error') return outcome;
        if (attempt < WAKE_ATTEMPTS - 1) {
            onWaiting('Waking up the server…');
            await new Promise((r) => setTimeout(r, WAKE_DELAY_MS));
        }
    }
    return outcome;
}

const LOCK_MESSAGES = {
    denied: 'That access code was not accepted.',
    throttled: 'Too many attempts. Wait a minute and try again.',
    offline: "You're offline. Connect to the internet to unlock.",
    error: 'The server is having a problem. Please try again in a moment.',
};

async function start() {
    showView('boot');
    const token = readToken();
    const { result, me: signedIn } = await checkAccessWithWake(token, (msg) => { bootEl.textContent = msg; });
    bootEl.textContent = 'Opening…';

    if (result === 'ok') return showApp(signedIn);
    if (result === 'denied') {
        clearToken();
        return showLock(token ? 'Your access code is no longer accepted. Please enter it again.' : '');
    }
    if (result === 'offline' && token) {   // can't verify, but nothing is shown that needs the server
        showApp();
        return setStatus("You're offline. Reconnect to get recommendations.", true);
    }
    return showLock(LOCK_MESSAGES[result]);
}

lockForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const value = tokenInput.value.trim();
    if (!value) return;
    unlockBtn.disabled = true;
    lockStatus.textContent = 'Checking…';
    const { result, me: signedIn } = await checkAccessWithWake(value, (msg) => { lockStatus.textContent = msg; });
    unlockBtn.disabled = false;
    if (result === 'ok') {
        saveToken(value, rememberChk.checked);
        tokenInput.value = '';
        return showApp(signedIn);
    }
    lockStatus.textContent = LOCK_MESSAGES[result];
    if (result === 'denied') {
        tokenInput.value = '';
        tokenInput.focus();
    }
});

lockBtn.addEventListener('click', () => {
    clearToken();
    showLock('');
});

// ---- API calls -------------------------------------------------------------------------------------------
function setStatus(message, isError = false) {
    statusEl.textContent = message;
    statusEl.classList.toggle('error', isError);
}

class ApiError extends Error {
    constructor(message, status) {
        super(message);
        this.status = status;
    }
}

// JSON in, JSON out (null for 204). Throws ApiError with a message that is safe to show.
async function request(method, path, payload) {
    if (!navigator.onLine) {
        throw new ApiError("You're offline. Reconnect and try again.", 0);
    }
    const headers = {};
    if (payload !== undefined) headers['Content-Type'] = 'application/json';
    const token = readToken();
    if (token) headers['X-Access-Token'] = token;

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    try {
        const resp = await fetch(path, {
            method,
            headers,
            body: payload === undefined ? undefined : JSON.stringify(payload),
            signal: controller.signal,
        });
        noteVersion(resp);
        let body = null;
        try { body = await resp.json(); } catch { /* 204 or non-JSON body */ }
        if (!resp.ok) {
            const detail = body && (body.detail || body.title);
            throw new ApiError(detail || `Request failed (${resp.status}).`, resp.status);
        }
        return body;
    } catch (e) {
        if (e.name === 'AbortError') throw new ApiError('The request timed out. Please try again.', 0);
        if (e instanceof ApiError) throw e;
        throw new ApiError("Couldn't reach the server. Check your connection.", 0);
    } finally {
        clearTimeout(timer);
    }
}

// A rejected access code anywhere sends you back to the lock screen.
function handleAuthOr(e, show) {
    if (e.status === 401) {
        clearToken();
        showLock('Your access code was rejected. Please enter it again.');
    } else {
        show(e.message);
    }
}

function setBusy(busy) {
    [analyzeBtn, songsBtn, albumsBtn].forEach((b) => { b.disabled = busy; });
    emotionsContainer.querySelectorAll('button').forEach((b) => { b.disabled = busy; });
    if (busy) saveBtn.disabled = true; else updateSelection();
}

async function run(action, busyMessage) {
    setBusy(true);
    setStatus(busyMessage);
    try {
        await action();
    } catch (e) {
        handleAuthOr(e, (msg) => setStatus(msg, true));
        if (e.status === 403) {                    // e.g. music was switched off on another device
            request('GET', '/api/me').then(applyMe).catch(() => {});
        }
    } finally {
        setBusy(false);
    }
}

// ---- check in --------------------------------------------------------------------------------------------
const label = (code) => code.charAt(0).toUpperCase() + code.slice(1);
const selectionKey = () => [...selected].sort().join(',');

function analyze() {
    const text = moodText.value.trim();
    if (!text) {
        setStatus('Please describe your mood first.', true);
        moodText.focus();
        return;
    }
    run(async () => {
        const data = await request('POST', '/api/analyze', { moodText: text });
        analyzed = { text, flagged: data.flagged === true };
        showEmotions(data.emotions);
    }, 'Analyzing…');
}

function renderStrength() {
    const host = $('strengthButtons');
    if (!host.children.length) {
        for (let i = 1; i <= 5; i++) {
            const b = document.createElement('button');
            b.type = 'button';
            b.className = 'strength-btn';
            b.dataset.value = String(i);
            b.textContent = String(i);
            b.setAttribute('aria-label', `Strength ${i} of 5, ${STRENGTH_NAME[i]}`);
            b.addEventListener('click', () => { strength = strength === i ? null : i; renderStrength(); });
            host.appendChild(b);
        }
    }
    const locked = selected.length > 0 && selectionKey() === lastSavedKey;
    host.querySelectorAll('button').forEach((b) => {
        b.setAttribute('aria-pressed', String(Number(b.dataset.value) === strength));
        b.disabled = locked;
    });
}

function showHint(emotion) {
    $('emotionHint').textContent = EMOTION_HELP[emotion] ? `${emotion[0].toUpperCase()}${emotion.slice(1)}: ${EMOTION_HELP[emotion]}` : '';
}

function showEmotions(emotions) {
    strength = null;
    $('emotionHint').textContent = 'Tap a word to see what it means.';
    emotionsContainer.replaceChildren();
    selected = [];
    lastSavedKey = null;
    resultsCard.hidden = true;
    flagNote.hidden = !analyzed.flagged;
    keepWords.checked = analyzed.flagged;     // ticked for you when the sentence sounded heavy; you can untick it
    if (!emotions || emotions.length === 0) {
        emotionsCard.hidden = true;
        mediaCard.hidden = true;
        setStatus('No emotions detected. Try describing it differently.', true);
        return;
    }
    emotions.forEach((em) => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'chip';
        btn.dataset.emotion = em;
        btn.textContent = em;
        btn.setAttribute('aria-pressed', 'false');
        btn.addEventListener('click', () => toggleEmotion(em));
        emotionsContainer.appendChild(btn);
    });
    emotionsCard.hidden = false;
    updateSelection();
    setStatus('Pick the ones that fit (up to 3).');
}

function toggleEmotion(emotion) {
    showHint(emotion);
    if (selected.includes(emotion)) {
        selected = selected.filter((e) => e !== emotion);
    } else if (selected.length >= MAX_EMOTIONS) {
        setStatus(`Up to ${MAX_EMOTIONS} at a time. Tap one to unselect it.`, true);
        return;
    } else {
        selected.push(emotion);
    }
    resultsCard.hidden = true;
    setStatus('');
    updateSelection();
}

function updateSelection() {
    emotionsContainer.querySelectorAll('.chip').forEach((chip) => {
        const on = selected.includes(chip.dataset.emotion);
        chip.classList.toggle('selected', on);
        chip.setAttribute('aria-pressed', String(on));
    });
    const alreadySaved = selected.length > 0 && selectionKey() === lastSavedKey;
    renderStrength();
    saveBtn.disabled = selected.length === 0 || alreadySaved;
    saveBtn.classList.toggle('saved', alreadySaved);
    newBtn.classList.toggle('primary', alreadySaved);   // after saving, "New check-in" is the natural next step
    saveBtn.textContent = alreadySaved ? 'Saved ✓' : selected.length ? `Save check-in (${selected.length})` : 'Save check-in';
    mediaCard.hidden = selected.length === 0 || !me.musicEnabled;
    mediaQuestion.textContent = selected.length ? `Songs or albums for ${selected.join(' + ')}?` : '';
}

function saveCheckIn() {
    if (selected.length === 0) return;
    const emotions = [...selected];
    run(async () => {
        const keep = keepWords.checked && analyzed.text;
        const payload = keep ? { emotions, note: analyzed.text, flagged: analyzed.flagged } : { emotions };
        if (strength) payload.intensity = strength;
        await request('POST', '/api/checkins', payload);
        lastSavedKey = [...emotions].sort().join(',');
        reportsStale = true;
        setStatus('Saved to your mood log.');
    }, 'Saving…');
}

function chooseMedia(mediaType) {
    if (!me.musicEnabled) {
        setStatus('Music suggestions are turned off. Use the Music button to turn them on.', true);
        return;
    }
    if (selected.length === 0) {
        setStatus('Select an emotion first.', true);
        return;
    }
    const emotions = [...selected];
    run(async () => {
        const data = await request('POST', '/api/recommend', { emotions, mediaType });
        showResults(data.items);
        setStatus(lastSavedKey === selectionKey() ? 'Saved to your mood log.' : '');
    }, 'Finding music…');
}

function showResults(items) {
    resultsList.replaceChildren();
    if (!items || items.length === 0) {
        const li = document.createElement('li');
        li.textContent = 'No recommendations this time. Try again.';
        resultsList.appendChild(li);
    } else {
        items.forEach((it) => {
            const li = document.createElement('li');
            const title = document.createElement('div');
            title.className = 'title';
            title.textContent = it.title || '';
            const artist = document.createElement('div');
            artist.className = 'artist';
            artist.textContent = it.artist || '';
            const desc = document.createElement('div');
            desc.className = 'desc';
            desc.textContent = it.description || '';
            li.append(title, artist, desc);
            resultsList.appendChild(li);
        });
    }
    resultsCard.hidden = false;
    resultsCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

analyzeBtn.addEventListener('click', analyze);
saveBtn.addEventListener('click', saveCheckIn);
newBtn.addEventListener('click', newCheckIn);
newBtn2.addEventListener('click', newCheckIn);
musicBtn.addEventListener('click', toggleMusic);
songsBtn.addEventListener('click', () => chooseMedia('SONGS'));
albumsBtn.addEventListener('click', () => chooseMedia('ALBUMS'));

// ---- tabs ------------------------------------------------------------------------------------------------
let reportsStale = true;

function setTab(name) {
    const reports = name === 'reports';
    const summary = name === 'summary';
    tabCheckin.setAttribute('aria-selected', String(!reports && !summary));
    tabReports.setAttribute('aria-selected', String(reports));
    $('tabSummary').setAttribute('aria-selected', String(summary));
    viewCheckin.hidden = reports || summary;
    viewReports.hidden = !reports;
    $('viewSummary').hidden = !summary;
    if (reports && reportsStale) loadReport();
    if (summary) loadSummary();
}
tabCheckin.addEventListener('click', () => setTab('checkin'));
tabReports.addEventListener('click', () => setTab('reports'));
$('tabSummary').addEventListener('click', () => setTab('summary'));

// ---- reports ---------------------------------------------------------------------------------------------
const SERIES_SLOT = { positive: 1, negative: 2, neutral: 3, body: 4 };   // fixed order, never cycled
const MIX_ORDER = ['positive', 'negative', 'neutral', 'body'];
const GROUP_NAME = { positive: 'Positive', negative: 'Negative', neutral: 'Neutral', body: 'Body' };

let range = 'week';
let report = null;
let tracked = [];                 // [{ code, slot }] - a slot stays with its emotion until it is unselected
const showingTable = { mood: false, intensity: false, mix: false, track: false };

const tz = () => { try { return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'; } catch { return 'UTC'; } };
const parseDay = (s) => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); };
const fmtDM = (d) => d.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
const fmtWD = (d) => d.toLocaleDateString(undefined, { weekday: 'short' });
const fmtWDDM = (d) => d.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
const fmtMood = (v) => {
    if (v === null || v === undefined) return '–';
    const r = Math.round(v * 10) / 10;                  // sign follows what is shown, so +0.0 never appears
    return (r > 0 ? '+' : r < 0 ? '−' : '') + Math.abs(r).toFixed(1);
};
const tickMood = (v) => (v > 0 ? `+${v}` : v < 0 ? `−${Math.abs(v)}` : '0');
const fmtCount = (v) => String(v ?? 0);

function clearReports() {
    ['moodChart', 'intensityChart', 'mixChart', 'trackChart', 'moodTable', 'intensityTable', 'mixTable', 'trackTable',
        'trackPicker', 'topList', 'historyList', 'summaryBody'].forEach((id) => $(id).replaceChildren());
    summaryData = null;
    $('summaryStatus').textContent = '';
    report = null;
    tracked = [];
    reportsStale = true;
    reportStatus.textContent = '';
}

async function loadReport() {
    reportBody.classList.add('loading');           // hold the previous render, dimmed, while refetching
    reportStatus.textContent = '';
    try {
        const [data, history] = await Promise.all([
            request('GET', `/api/reports?range=${encodeURIComponent(range)}&tz=${encodeURIComponent(tz())}`),
            request('GET', '/api/checkins?limit=20'),
        ]);
        report = data;
        reportsStale = false;
        renderReport(data);
        renderHistory(history);
    } catch (e) {
        handleAuthOr(e, (msg) => { reportStatus.textContent = msg; reportStatus.classList.add('error'); });
    } finally {
        reportBody.classList.remove('loading');
    }
}

function bucketLabels(data) {
    const daily = data.bucketDays === 1;
    const labels = data.buckets.map((b) => {
        const d = parseDay(b.start);
        return daily ? (data.buckets.length <= 7 ? fmtWD(d) : fmtDM(d)) : fmtDM(d);
    });
    const titles = data.buckets.map((b) => {
        const d = parseDay(b.start);
        return daily ? fmtWDDM(d) : `${fmtDM(d)} – ${fmtDM(parseDay(b.end))}`;
    });
    return { labels, titles };
}

function setEmpty(hostId, empty, message) {
    const host = $(hostId);
    host.replaceChildren();
    if (empty) {
        const p = document.createElement('p');
        p.className = 'empty';
        p.textContent = message;
        host.appendChild(p);
    }
    return !empty;
}

function renderReport(data) {
    $('reportSpan').textContent = `${fmtDM(parseDay(data.from))} – ${fmtDM(parseDay(data.to))}`;
    const s = data.summary;
    const totalDays = data.buckets.length * data.bucketDays;
    const previousLabel = `previous ${totalDays} days`;

    $('tileCheckins').textContent = String(s.checkIns);
    $('tileCheckinsNote').textContent = `${s.previousCheckIns} in ${previousLabel}`;
    $('tileDays').textContent = String(s.activeDays);
    $('tileDaysNote').textContent = `of ${totalDays} days`;
    $('tileMood').textContent = fmtMood(s.avgMood);
    const note = $('tileMoodNote');
    const period = $('tileMoodPeriod');
    note.className = 'tile-delta';
    period.textContent = '';
    const change = (s.moodChange === null || s.moodChange === undefined) ? null : Math.round(s.moodChange * 10) / 10;
    if (change === null) {
        note.textContent = s.avgMood === null ? 'No mood scores yet' : 'No previous data';
    } else if (change === 0) {
        note.textContent = 'No change';
        period.textContent = `vs ${previousLabel}`;
    } else {
        const up = change > 0;
        note.classList.add(up ? 'up' : 'down');
        note.textContent = `${up ? '▲' : '▼'} ${Math.abs(change).toFixed(1)}`;   // arrow + number: never color alone
        period.textContent = `vs ${previousLabel}`;
    }

    const hasData = s.checkIns > 0;
    const { labels, titles } = bucketLabels(data);
    const message = 'No check-ins in this period yet.';

    // 1. mood over time (one series, so no legend; the card title names it)
    const moodCfg = {
        labels, titles, ariaLabel: 'Mood over time, from minus 2 (low) to plus 2 (high).',
        series: [{ name: 'Mood', slot: 1, values: data.buckets.map((b) => b.avgMood), fmt: fmtMood }],
        y: { min: -2, max: 2, ticks: [-2, -1, 0, 1, 2], fmt: tickMood }, baseline: 0,
        points: data.buckets.length <= 7,
        endLabel: (series) => {
            const last = [...series.values].reverse().find((v) => v !== null && v !== undefined);
            return last === undefined ? null : fmtMood(last);
        },
    };
    drawCard('mood', moodCfg, hasData, message, (host) => lineChart(host, moodCfg));

    // 1b. how strong it felt (only check-ins where a strength was given)
    const strengthCfg = {
        labels, titles, ariaLabel: 'Average strength per period, from 1 (barely) to 5 (very strong).',
        series: [{ name: 'Strength', slot: 1, values: data.buckets.map((b) => b.avgIntensity), fmt: fmtStrength }],
        y: { min: 1, max: 5, ticks: [1, 2, 3, 4, 5], fmt: (v) => String(v) },
        points: data.buckets.length <= 7,
        endLabel: (series) => {
            const last = [...series.values].reverse().find((v) => v !== null && v !== undefined);
            return last === undefined ? null : fmtStrength(last);
        },
    };
    const hasStrength = data.buckets.some((b) => b.avgIntensity !== null && b.avgIntensity !== undefined);
    drawCard('intensity', strengthCfg, hasStrength, hasData ? 'No strength given yet. Pick 1 to 5 when you save a check-in.' : message,
        (host) => lineChart(host, strengthCfg));

    // 2. what you felt, by group
    const mixSeries = MIX_ORDER.map((g) => ({
        name: GROUP_NAME[g], slot: SERIES_SLOT[g], values: data.buckets.map((b) => b.groups[g] || 0), fmt: fmtCount,
    }));
    const maxStack = Math.max(0, ...data.buckets.map((b) => MIX_ORDER.reduce((sum, g) => sum + (b.groups[g] || 0), 0)));
    const mixCfg = { labels, titles, series: mixSeries, y: niceCountAxis(maxStack),
        ariaLabel: 'Emotions picked per period, stacked by group.' };
    drawCard('mix', mixCfg, hasData, message, (host) => stackedBarChart(host, mixCfg));

    // 3. track an emotion
    renderTracker(labels, titles, hasData, message);

    // 4. most frequent
    renderTop(data.emotions);
}

function drawCard(key, cfg, hasData, message, draw) {
    const host = $(`${key}Chart`);
    const tableHost = $(`${key}Table`);
    const button = document.querySelector(`[data-table-for="${key}"]`);
    tableHost.replaceChildren();
    if (!setEmpty(`${key}Chart`, !hasData, message)) {
        button.hidden = true;
        tableHost.hidden = true;
        return;
    }
    button.hidden = false;
    draw(host);
    tableHost.appendChild(tableFor(cfg, report.bucketDays === 1 ? 'Day' : 'Week'));
    applyTableState(key);
}

function applyTableState(key) {
    const on = showingTable[key];
    $(`${key}Chart`).hidden = on;
    $(`${key}Table`).hidden = !on;
    const button = document.querySelector(`[data-table-for="${key}"]`);
    button.textContent = on ? 'Chart' : 'Table';
}

document.querySelectorAll('[data-table-for]').forEach((button) => {
    button.addEventListener('click', () => {
        const key = button.dataset.tableFor;
        showingTable[key] = !showingTable[key];
        applyTableState(key);
    });
});

function renderTracker(labels, titles, hasData, message) {
    const emotions = report.emotions;
    const present = new Set(emotions.map((e) => e.emotion));
    tracked = tracked.filter((t) => present.has(t.code));           // survivors keep their color
    if (tracked.length === 0) {
        emotions.slice(0, 3).forEach((e, i) => tracked.push({ code: e.emotion, slot: i + 1 }));
    }

    const picker = $('trackPicker');
    picker.replaceChildren();
    emotions.slice(0, 12).forEach((e) => {
        const on = tracked.find((t) => t.code === e.emotion);
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'chip pick';
        btn.setAttribute('aria-pressed', String(!!on));
        if (on) {
            const key = document.createElement('span');
            key.className = `swatch swatch-line sw${on.slot}`;
            btn.appendChild(key);
        }
        btn.appendChild(document.createTextNode(e.emotion));
        btn.addEventListener('click', () => toggleTracked(e.emotion));
        picker.appendChild(btn);
    });

    const cfg = {
        labels, titles, ariaLabel: 'How often each chosen emotion came up per period.',
        series: tracked.map((t) => ({
            name: label(t.code), slot: t.slot, fmt: fmtCount,
            values: report.buckets.map((b) => b.emotions[t.code] || 0),
        })),
        y: niceCountAxis(Math.max(0, ...tracked.flatMap((t) => report.buckets.map((b) => b.emotions[t.code] || 0)))),
        points: false, endLabel: (series) => series.name,
    };
    if (tracked.length === 0) {
        $('trackChart').replaceChildren();
        $('trackTable').replaceChildren();
        if (hasData) setEmpty('trackChart', true, 'Pick an emotion above to follow it over time.');
        else setEmpty('trackChart', true, message);
        document.querySelector('[data-table-for="track"]').hidden = true;
        return;
    }
    drawCard('track', cfg, hasData, message, (host) => lineChart(host, cfg));
}

function toggleTracked(code) {
    const at = tracked.findIndex((t) => t.code === code);
    if (at >= 0) {
        tracked.splice(at, 1);
    } else if (tracked.length >= 3) {
        reportStatus.textContent = 'Up to 3 emotions at a time. Tap one to unselect it.';
        reportStatus.classList.add('error');
        return;
    } else {
        const used = new Set(tracked.map((t) => t.slot));
        tracked.push({ code, slot: [1, 2, 3].find((n) => !used.has(n)) });
    }
    reportStatus.textContent = '';
    const { labels, titles } = bucketLabels(report);
    renderTracker(labels, titles, report.summary.checkIns > 0, 'No check-ins in this period yet.');
}

function renderTop(stats) {
    const list = $('topList');
    list.replaceChildren();
    $('topEmpty').hidden = stats.length > 0;
    const max = Math.max(1, ...stats.map((e) => e.count));
    stats.slice(0, 8).forEach((e) => {
        const li = document.createElement('li');
        const name = document.createElement('span');
        name.className = 'rank-name';
        name.textContent = e.emotion;
        const track = document.createElement('span');
        track.className = 'rank-track';
        const bar = document.createElement('div');
        bar.className = 'rank-bar';
        bar.style.width = `${Math.round((e.count / max) * 100)}%`;
        track.appendChild(bar);
        const count = document.createElement('span');
        count.className = 'rank-count';
        count.textContent = String(e.count);
        const days = document.createElement('span');
        days.className = 'rank-days';
        days.textContent = e.days === 1 ? 'on 1 day' : `on ${e.days} days`;
        li.append(name, track, count, days);
        list.appendChild(li);
    });
}

function renderHistory(items) {
    const list = $('historyList');
    list.replaceChildren();
    $('historyEmpty').hidden = items.length > 0;
    items.forEach((item) => {
        const li = document.createElement('li');
        const info = document.createElement('div');
        const when = document.createElement('div');
        when.className = 'h-when';
        when.textContent = new Date(item.createdAt).toLocaleString(undefined,
            { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
        const emotions = document.createElement('div');
        emotions.className = 'h-emotions';
        emotions.textContent = item.emotions.join(', ');
        info.append(when, emotions);
        if (item.intensity) {
            const st = document.createElement('div');
            st.className = 'h-strength';
            st.textContent = `Strength ${item.intensity}/5 (${STRENGTH_NAME[item.intensity]})`;
            info.appendChild(st);
        }
        if (item.note) {
            const note = document.createElement('div');
            note.className = 'h-note';
            note.textContent = item.note;
            info.appendChild(note);
        }
        if (item.flagged) {
            const flag = document.createElement('div');
            flag.className = 'h-flag';
            flag.textContent = '⚑ Sounded heavy';
            info.appendChild(flag);
        }
        const del = document.createElement('button');
        del.type = 'button';
        del.className = 'link-btn';
        del.textContent = 'Delete';
        del.setAttribute('aria-label', `Delete the check-in with ${item.emotions.join(', ')}`);
        del.addEventListener('click', () => deleteCheckIn(item.id));
        li.append(info, del);
        list.appendChild(li);
    });
}

async function deleteCheckIn(id) {
    if (!window.confirm('Delete this check-in? This cannot be undone.')) return;
    try {
        await request('DELETE', `/api/checkins/${id}`);
        loadReport();
    } catch (e) {
        handleAuthOr(e, (msg) => { reportStatus.textContent = msg; reportStatus.classList.add('error'); });
    }
}

document.querySelectorAll('.range-btn').forEach((button) => {
    button.addEventListener('click', () => {
        range = button.dataset.range;
        document.querySelectorAll('.range-btn').forEach((b) => b.setAttribute('aria-pressed', String(b === button)));
        loadReport();
    });
});

// Your backup: everything you have saved, as a JSON file.
$('exportBtn').addEventListener('click', async () => {
    try {
        const headers = {};
        const token = readToken();
        if (token) headers['X-Access-Token'] = token;
        const resp = await fetch('/api/export', { headers, cache: 'no-store' });
        if (resp.status === 401) throw new ApiError('', 401);
        if (!resp.ok) throw new ApiError('The export failed. Please try again.', resp.status);
        const blob = await resp.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `moodtunes-export-${new Date().toISOString().slice(0, 10)}.json`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 5000);
        reportStatus.textContent = 'Export ready.';
        reportStatus.classList.remove('error');
    } catch (e) {
        handleAuthOr(e, (msg) => { reportStatus.textContent = msg; reportStatus.classList.add('error'); });
    }
});

// Offline support / installability
if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('/sw.js').catch((e) => console.warn('Service worker failed', e));
    });
}

start();

// ---- summary for a therapy session ------------------------------------------------------------------------
async function loadSummary() {
    const status = $('summaryStatus');
    status.textContent = 'Loading…';
    try {
        summaryData = await request('GET', `/api/summary?tz=${encodeURIComponent(tz())}`);
        renderSummary(summaryData);
        status.textContent = '';
    } catch (e) {
        handleAuthOr(e, (msg) => { status.textContent = msg; });
    }
}

const fmtTime = (iso) => new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
const dayKey = (iso) => new Date(iso).toLocaleDateString('en-CA');           // yyyy-mm-dd in the viewer's time zone
const cap = (w) => w.charAt(0).toUpperCase() + w.slice(1);
const plural = (n, one, many) => `${n} ${n === 1 ? one : many}`;

/** The summary as plain facts, used for both the page and the copied text so they never disagree. */
function summaryFacts(data) {
    const r = data.report;
    const s = r.summary;
    const change = (s.moodChange === null || s.moodChange === undefined) ? null : Math.round(s.moodChange * 10) / 10;
    const facts = [
        ['Check-ins', `${s.checkIns} (${s.previousCheckIns} the week before)`],
        ['Days with a check-in', `${s.activeDays} of 7`],
        ['Average mood (−2 low to +2 high)', s.avgMood === null ? 'no scores yet'
            : `${fmtMood(s.avgMood)}${change === null || change === 0 ? '' : ` (${change > 0 ? 'up' : 'down'} ${Math.abs(change).toFixed(1)} on the week before)`}`],
    ];
    if (s.avgIntensity !== null && s.avgIntensity !== undefined) {
        facts.push(['Average strength (1 barely to 5 very strong)', s.avgIntensity.toFixed(1)]);
    }
    const top = r.emotions.slice(0, 5).map((e) => `${cap(e.emotion)} (${plural(e.count, 'time', 'times')}, ${plural(e.days, 'day', 'days')})`);
    const days = [];
    data.checkIns.forEach((c) => {
        const k = dayKey(c.createdAt);
        let d = days[days.length - 1];
        if (!d || d.key !== k) {
            d = { key: k, label: new Date(c.createdAt).toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' }), items: [] };
            days.push(d);
        }
        d.items.push(c);
    });
    return { facts, top, days, heavy: data.checkIns.filter((c) => c.flagged),
        title: `Week of ${fmtDM(parseDay(r.from))} to ${fmtDM(parseDay(r.to))}` };
}

const entryLine = (c) => `${fmtTime(c.createdAt)}  ${c.emotions.map(cap).join(', ')}${c.intensity ? `  (strength ${c.intensity}/5)` : ''}`;

function renderSummary(data) {
    const f = summaryFacts(data);
    $('summaryRange').textContent = f.title;
    $('summaryWho').textContent = `MoodTunes, ${me.name}. ${f.title}. Printed ${new Date().toLocaleDateString()}.`;
    const body = $('summaryBody');
    body.replaceChildren();
    if (data.checkIns.length === 0) {
        const p = document.createElement('p');
        p.className = 'empty';
        p.textContent = 'No check-ins in the last 7 days yet.';
        body.appendChild(p);
        return;
    }
    const heading = (text) => { const h = document.createElement('h3'); h.className = 'sum-h'; h.textContent = text; return h; };

    const list = document.createElement('ul');
    list.className = 'sum-list';
    f.facts.forEach(([k, v]) => {
        const li = document.createElement('li');
        const a = document.createElement('span'); a.textContent = k;
        const b = document.createElement('span'); b.className = 'v'; b.textContent = v;
        li.append(a, b);
        list.appendChild(li);
    });
    body.appendChild(list);

    if (f.top.length) {
        body.appendChild(heading('Most frequent'));
        const p = document.createElement('p');
        p.textContent = f.top.join('; ');
        body.appendChild(p);
    }
    if (f.heavy.length) {
        body.appendChild(heading('Moments that sounded heavy'));
        const ul = document.createElement('ul');
        f.heavy.forEach((c) => {
            const li = document.createElement('li');
            li.textContent = `${new Date(c.createdAt).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })}, ${fmtTime(c.createdAt)}${c.note ? `: "${c.note}"` : ''}`;
            ul.appendChild(li);
        });
        body.appendChild(ul);
    }
    body.appendChild(heading('Check-ins'));
    f.days.forEach((d) => {
        const dh = document.createElement('div');
        dh.className = 'sum-day';
        dh.textContent = d.label;
        body.appendChild(dh);
        d.items.forEach((c) => {
            const div = document.createElement('div');
            div.className = 'sum-entry';
            const t = document.createElement('span'); t.className = 't'; t.textContent = `${fmtTime(c.createdAt)}  `;
            const e = document.createElement('span'); e.className = 'e'; e.textContent = c.emotions.join(', ');
            div.append(t, e);
            if (c.intensity) {
                const st = document.createElement('span'); st.className = 't'; st.textContent = `  strength ${c.intensity}/5`;
                div.appendChild(st);
            }
            if (c.note) {
                const q = document.createElement('blockquote'); q.textContent = c.note;
                div.appendChild(q);
            }
            body.appendChild(div);
        });
    });
}

function summaryText(data) {
    const f = summaryFacts(data);
    const lines = [`MoodTunes, ${me.name}`, f.title, ''];
    f.facts.forEach(([k, v]) => lines.push(`${k}: ${v}`));
    if (f.top.length) lines.push('', `Most frequent: ${f.top.join('; ')}`);
    if (f.heavy.length) {
        lines.push('', 'Moments that sounded heavy:');
        f.heavy.forEach((c) => lines.push(`- ${new Date(c.createdAt).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })} ${fmtTime(c.createdAt)}${c.note ? `: "${c.note}"` : ''}`));
    }
    lines.push('', 'Check-ins:');
    f.days.forEach((d) => {
        lines.push(d.label);
        d.items.forEach((c) => {
            lines.push(`  ${entryLine(c)}`);
            if (c.note) lines.push(`    "${c.note}"`);
        });
    });
    return lines.join('\n');
}

$('printBtn').addEventListener('click', () => window.print());
$('copyBtn').addEventListener('click', async () => {
    const status = $('summaryStatus');
    if (!summaryData) return;
    try {
        await navigator.clipboard.writeText(summaryText(summaryData));
        status.textContent = 'Copied. Paste it into a message or note.';
    } catch {
        status.textContent = 'Copying is not allowed here. Use Print / save as PDF instead.';
    }
});
