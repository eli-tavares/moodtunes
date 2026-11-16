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
let memoryToken = '';         // fallback when browser storage is unavailable (private mode, blocked storage)

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

function resetApp() {
    moodText.value = '';
    emotionsContainer.replaceChildren();
    resultsList.replaceChildren();
    emotionsCard.hidden = true;
    mediaCard.hidden = true;
    resultsCard.hidden = true;
    selected = [];
    lastSavedKey = null;
    setStatus('');
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

function showApp() {
    lockStatus.textContent = '';
    showView('app');
}

// ---- access check ----------------------------------------------------------------------------------------
// -> 'ok' | 'denied' | 'throttled' | 'offline' | 'error'
async function checkAccess(token) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), CHECK_TIMEOUT_MS);
    try {
        const resp = await fetch('/api/auth/check', {
            headers: token ? { 'X-Access-Token': token } : {},
            cache: 'no-store',
            signal: controller.signal,
        });
        if (resp.ok) return 'ok';
        if (resp.status === 401) return 'denied';
        if (resp.status === 429) return 'throttled';
        return 'error';
    } catch {
        return 'offline';
    } finally {
        clearTimeout(timer);
    }
}

// Retries 5xx answers (a sleeping/booting host), so this first call also wakes the server for the real ones.
async function checkAccessWithWake(token, onWaiting) {
    let result = 'error';
    for (let attempt = 0; attempt < WAKE_ATTEMPTS; attempt++) {
        result = await checkAccess(token);
        if (result !== 'error') return result;
        if (attempt < WAKE_ATTEMPTS - 1) {
            onWaiting('Waking up the server…');
            await new Promise((r) => setTimeout(r, WAKE_DELAY_MS));
        }
    }
    return result;
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
    const result = await checkAccessWithWake(token, (msg) => { bootEl.textContent = msg; });
    bootEl.textContent = 'Opening…';

    if (result === 'ok') return showApp();
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
    const result = await checkAccessWithWake(value, (msg) => { lockStatus.textContent = msg; });
    unlockBtn.disabled = false;
    if (result === 'ok') {
        saveToken(value, rememberChk.checked);
        tokenInput.value = '';
        return showApp();
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
        showEmotions(data.emotions);
    }, 'Analyzing…');
}

function showEmotions(emotions) {
    emotionsContainer.replaceChildren();
    selected = [];
    lastSavedKey = null;
    resultsCard.hidden = true;
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
    saveBtn.disabled = selected.length === 0 || alreadySaved;
    saveBtn.classList.toggle('saved', alreadySaved);
    saveBtn.textContent = alreadySaved ? 'Saved ✓' : selected.length ? `Save check-in (${selected.length})` : 'Save check-in';
    mediaCard.hidden = selected.length === 0;
    mediaQuestion.textContent = selected.length ? `Songs or albums for ${selected.join(' + ')}?` : '';
}

function saveCheckIn() {
    if (selected.length === 0) return;
    const emotions = [...selected];
    run(async () => {
        await request('POST', '/api/checkins', { emotions });
        lastSavedKey = [...emotions].sort().join(',');
        reportsStale = true;
        setStatus('Saved to your mood log.');
    }, 'Saving…');
}

function chooseMedia(mediaType) {
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
songsBtn.addEventListener('click', () => chooseMedia('SONGS'));
albumsBtn.addEventListener('click', () => chooseMedia('ALBUMS'));

// ---- tabs ------------------------------------------------------------------------------------------------
let reportsStale = true;

function setTab(name) {
    const reports = name === 'reports';
    tabCheckin.setAttribute('aria-selected', String(!reports));
    tabReports.setAttribute('aria-selected', String(reports));
    viewCheckin.hidden = reports;
    viewReports.hidden = !reports;
    if (reports && reportsStale) loadReport();
}
tabCheckin.addEventListener('click', () => setTab('checkin'));
tabReports.addEventListener('click', () => setTab('reports'));

// ---- reports ---------------------------------------------------------------------------------------------
const SERIES_SLOT = { positive: 1, negative: 2, neutral: 3, body: 4 };   // fixed order, never cycled
const MIX_ORDER = ['positive', 'negative', 'neutral', 'body'];
const GROUP_NAME = { positive: 'Positive', negative: 'Negative', neutral: 'Neutral', body: 'Body' };

let range = 'week';
let report = null;
let tracked = [];                 // [{ code, slot }] - a slot stays with its emotion until it is unselected
const showingTable = { mood: false, mix: false, track: false };

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
    ['moodChart', 'mixChart', 'trackChart', 'moodTable', 'mixTable', 'trackTable', 'trackPicker', 'topList',
        'historyList'].forEach((id) => $(id).replaceChildren());
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
