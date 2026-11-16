'use strict';

const TOKEN_KEY = 'moodtunes.accessToken';
const REQUEST_TIMEOUT_MS = 45000;
const CHECK_TIMEOUT_MS = 10000;
const WAKE_ATTEMPTS = 4;          // a sleeping host can answer 502 for a few seconds while it boots
const WAKE_DELAY_MS = 2500;

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
const mediaCard = $('mediaCard');
const mediaQuestion = $('mediaQuestion');
const songsBtn = $('songsBtn');
const albumsBtn = $('albumsBtn');
const resultsCard = $('resultsCard');
const resultsList = $('resultsList');
const statusEl = $('status');

let selectedEmotion = null;
let memoryToken = '';   // fallback when browser storage is unavailable (private mode, blocked storage)

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
    selectedEmotion = null;
    setStatus('');
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

// ---- the app itself --------------------------------------------------------------------------------------
function setStatus(message, isError = false) {
    statusEl.textContent = message;
    statusEl.classList.toggle('error', isError);
}

function setBusy(busy) {
    [analyzeBtn, songsBtn, albumsBtn].forEach((b) => { b.disabled = busy; });
    emotionsContainer.querySelectorAll('button').forEach((b) => { b.disabled = busy; });
}

class ApiError extends Error {
    constructor(message, status) {
        super(message);
        this.status = status;
    }
}

async function api(path, payload) {
    if (!navigator.onLine) {
        throw new ApiError("You're offline. Reconnect and try again.", 0);
    }
    const headers = { 'Content-Type': 'application/json' };
    const token = readToken();
    if (token) headers['X-Access-Token'] = token;

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    try {
        const resp = await fetch(path, {
            method: 'POST',
            headers,
            body: JSON.stringify(payload),
            signal: controller.signal,
        });
        let body = null;
        try { body = await resp.json(); } catch { /* non-JSON error body */ }
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

// Runs an API action with busy/status handling; a rejected access code sends you back to the lock screen.
async function run(action, busyMessage) {
    setBusy(true);
    setStatus(busyMessage);
    try {
        await action();
    } catch (e) {
        if (e.status === 401) {
            clearToken();
            showLock('Your access code was rejected. Please enter it again.');
        } else {
            setStatus(e.message, true);
        }
    } finally {
        setBusy(false);
    }
}

function analyze() {
    const text = moodText.value.trim();
    if (!text) {
        setStatus('Please describe your mood first.', true);
        moodText.focus();
        return;
    }
    run(async () => {
        const data = await api('/api/analyze', { moodText: text });
        showEmotions(data.emotions);
    }, 'Analyzing…');
}

function showEmotions(emotions) {
    emotionsContainer.replaceChildren();
    selectedEmotion = null;
    mediaCard.hidden = true;
    resultsCard.hidden = true;
    if (!emotions || emotions.length === 0) {
        emotionsCard.hidden = true;
        setStatus('No emotions detected. Try describing it differently.', true);
        return;
    }
    emotions.forEach((em) => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'chip';
        btn.textContent = em;
        btn.addEventListener('click', () => selectEmotion(em, btn));
        emotionsContainer.appendChild(btn);
    });
    emotionsCard.hidden = false;
    setStatus('Select the emotion that fits.');
}

function selectEmotion(emotion, button) {
    selectedEmotion = emotion;
    emotionsContainer.querySelectorAll('.chip').forEach((c) => {
        c.classList.toggle('selected', c === button);
        c.setAttribute('aria-pressed', String(c === button));
    });
    mediaQuestion.textContent = `Songs or albums for "${emotion}"?`;
    mediaCard.hidden = false;
    resultsCard.hidden = true;
    setStatus('');
}

function chooseMedia(mediaType) {
    if (!selectedEmotion) {
        setStatus('Select an emotion first.', true);
        return;
    }
    const emotion = selectedEmotion;
    run(async () => {
        const data = await api('/api/recommend', { emotion, mediaType });
        showResults(data.items);
        setStatus('');
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
songsBtn.addEventListener('click', () => chooseMedia('SONGS'));
albumsBtn.addEventListener('click', () => chooseMedia('ALBUMS'));

// Offline support / installability
if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('/sw.js').catch((e) => console.warn('Service worker failed', e));
    });
}

start();
