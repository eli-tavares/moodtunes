'use strict';

const TOKEN_KEY = 'moodtunes.accessToken';
const REQUEST_TIMEOUT_MS = 45000;

const $ = (id) => document.getElementById(id);
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
const tokenCard = $('tokenCard');
const tokenInput = $('tokenInput');
const statusEl = $('status');

let selectedEmotion = null;
let pendingRetry = null; // action to re-run after the user enters an access code

// localStorage can throw (private mode, blocked storage) - the app must work without it.
function readToken() {
    try { return localStorage.getItem(TOKEN_KEY) || ''; } catch { return ''; }
}
function saveToken(value) {
    try { localStorage.setItem(TOKEN_KEY, value); } catch { /* ignore */ }
}

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

// Runs an API action with busy/status handling and the access-code prompt on 401.
async function run(action, busyMessage) {
    setBusy(true);
    setStatus(busyMessage);
    try {
        await action();
        tokenCard.hidden = true;
    } catch (e) {
        if (e.status === 401) {
            pendingRetry = () => run(action, busyMessage);
            tokenCard.hidden = false;
            tokenInput.focus();
            setStatus(readToken() ? 'That access code was not accepted.' : 'Please enter your access code.', true);
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
$('tokenSaveBtn').addEventListener('click', () => {
    const value = tokenInput.value.trim();
    if (!value) return;
    saveToken(value);
    tokenInput.value = '';
    tokenCard.hidden = true;
    if (pendingRetry) {
        const retry = pendingRetry;
        pendingRetry = null;
        retry();
    }
});

// Offline support / installability
if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('/sw.js').catch((e) => console.warn('Service worker failed', e));
    });
}
