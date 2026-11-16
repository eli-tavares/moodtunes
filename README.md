# 🎧 Welcome to Your Mood-Based Music Companion!

This little app is designed to help you discover music that truly matches how you feel.
Just tell it what’s going on — a sentence, a thought, a vibe — and the AI will read the mood behind your words and
suggest emotions that might fit.

Pick the emotion that feels right, choose whether you want songs or albums, and you’ll get a curated list of
recommendations tailored to your emotional moment.

It’s simple, personal, and a fun way to explore music you might’ve never found otherwise.
Whether you’re feeling on top of the world or a bit lost in your thoughts, there’s a soundtrack waiting for you. 🎶💛

---

# 🎵 MoodTunes

A **Spring Boot** app that uses **Spring AI (OpenAI)** to recommend songs or albums based on how you feel. It ships a
small mobile-first web UI that is also an installable **PWA**, so it can live on your iPhone home screen.

---

## 🚀 Run it

**Prerequisites:** Java 21+, Maven 3.9+ (or use Docker), and an API key for OpenAI **or** Anthropic (Claude).

```bash
export OPENAI_API_KEY=sk-...
mvn spring-boot:run          # http://localhost:8080
```

Docker:

```bash
docker compose up --build    # needs OPENAI_API_KEY in your environment
```

### Configuration

| Variable | Purpose |
|----------|---------|
| `OPENAI_API_KEY` | Your OpenAI key (default provider). |
| `SPRING_PROFILES_ACTIVE=claude` | Use Claude instead of OpenAI (see "Choosing the AI provider"). |
| `ANTHROPIC_API_KEY` | Your Anthropic API key (only with the `claude` profile). |
| `ANTHROPIC_MODEL` | Claude model (default `claude-haiku-5-5`). |
| `APP_ACCESS_TOKEN` | Optional shared secret. If set, every `/api` call must send it in the `X-Access-Token` header. The web UI asks for it once and stores it on the device. **Set this whenever the app is reachable from the internet** - otherwise anyone with the URL can spend your OpenAI credit. |
| `OPENAI_MODEL` | Model to use (default `gpt-6-luna`). See "Keeping AI cost low". |
| `PORT` | HTTP port (default 8080). |

Rate limits (per client and global, per minute) are under `app.rate-limit.*` in `application.yaml`.

---

## 🔀 Choosing the AI provider

The app talks to Spring AI's provider-neutral `ChatClient`, so the provider is just configuration:

```bash
# OpenAI (default)
export OPENAI_API_KEY=sk-...
mvn spring-boot:run

# Claude
export ANTHROPIC_API_KEY=sk-ant-...
SPRING_PROFILES_ACTIVE=claude mvn spring-boot:run
```

Only the selected provider's key is needed. Note that an Anthropic **API key** is separate from a claude.ai
subscription (Pro/Max): create one in the Anthropic Console, where API usage is billed by tokens.
To add another provider (Gemini, Mistral, Ollama, ...), add its `spring-ai-starter-model-*` dependency, a small
`application-<name>.yaml` that sets `spring.ai.model.chat` plus its key/model, and run with that profile.

---

## 💸 Keeping AI cost low

- Default model is `gpt-6-luna` ($0.10 / 1M input tokens, $0.50 / 1M output - cheaper than the `gpt-4o-mini` it replaces,
  which OpenAI shuts down on 2027-01-14). Prices change; check the
  [OpenAI pricing page](https://developers.openai.com/api/docs/pricing) and override with `OPENAI_MODEL` if needed.
- `claude-haiku-5-5` is priced the same ($0.10 / $0.50 per 1M tokens, see the
  [Claude pricing page](https://platform.claude.com/docs/en/about-claude/pricing)), so choose by answer quality.
- Recommendations are cached for 12 hours per emotion + songs/albums, so repeat picks cost nothing.
- Requests are tiny (a short mood sentence in, a short list out), the OpenAI call fails fast (30s timeout, 2 attempts)
  and the global rate limit (`app.rate-limit.global-per-minute`) caps the worst case.
- Also set a monthly spending limit in your OpenAI account as a final safety net.

---

## 📱 Use it on your iPhone

The app is a Progressive Web App. iOS requires **HTTPS** for installable web apps, so it has to be hosted somewhere your
phone can reach over HTTPS (a cloud host, or your own machine exposed privately, e.g. with Tailscale).

1. Deploy it (see Docker above) and open the HTTPS URL in **Safari** on the iPhone.
2. Tap **Share → Add to Home Screen**.
3. Launch **MoodTunes** from the home screen - it opens full screen like a native app.
4. If you set `APP_ACCESS_TOKEN`, enter it when prompted (once per device).

The app shell is cached by a service worker so it opens instantly and shows a friendly message when offline;
recommendations always need a connection because they come from the AI.

---

## 🔌 API

Stateless JSON API (errors are [RFC 7807](https://datatracker.ietf.org/doc/html/rfc7807) `application/problem+json`):

- `POST /api/analyze` - `{ "moodText": "..." }` (max 500 chars) -> `{ "emotions": ["joy", ...] }`
- `POST /api/recommend` - `{ "emotion": "joy", "mediaType": "SONGS|ALBUMS" }` ->
  `{ "items": [{ "title": "...", "artist": "...", "description": "..." }] }`
- `GET /actuator/health` - liveness/readiness for hosting platforms

Status codes: `400` invalid input, `401` missing/wrong access token, `413` body too large, `429` rate limited,
`502` the AI failed or answered with something unusable (never cached, so a retry works).

---

## 🛡️ Security notes

- Runs on Spring Boot 4 / Spring AI 2 with no known vulnerabilities at the time of writing. Re-check any time with
  [Trivy](https://trivy.dev): `trivy fs .`
- Input is validated and length-limited; model output is treated as untrusted and sanitised before reaching the browser.
- Strict CSP and other security headers on every response; no third-party scripts, styles or fonts.
- Upstream/AI error details are logged server-side only and never returned to clients.
- Container runs as a non-root user.

---

## 🧪 Tests

```bash
mvn test
```

Covers the API contract and validation, auth/rate limiting, error handling, AI-output parsing/sanitising and caching.
No OpenAI key is needed - the AI client is mocked.

---

## 🧠 How it works

1. The UI sends your mood text to `/api/analyze`; `MoodAnalysisService` asks the model for a handful of emotions.
2. You pick an emotion and songs/albums; the UI calls `/api/recommend`.
3. `MusicService` asks the model for a JSON list, parses and validates it, and caches the parsed result
   (Caffeine, key `emotion_mediaType`, 12 h, max 500 entries).

---

## 📄 License

MIT - see [LICENSE](LICENSE). App icon: Twemoji 🎶 (CC-BY 4.0), see `static/favicon/about.txt`.

Built with [Spring Boot](https://spring.io/projects/spring-boot), [Spring AI](https://docs.spring.io/spring-ai/)
and [Caffeine](https://github.com/ben-manes/caffeine).
