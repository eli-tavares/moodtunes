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

A **Spring Boot** app that uses **Spring AI (OpenAI or Claude)** to turn how you feel into emotions, recommend songs or
albums for them, and keep a private mood log with week / month / 3-month reports. It ships a
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

Instead of environment variables you can keep your settings in a git-ignored `application.properties` in the project
folder (Spring Boot picks it up automatically when you run from there; create it yourself - it is never committed):

```properties
spring.profiles.active=claude
spring.ai.anthropic.api-key=sk-ant-...
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

## 🚂 Deploy on Railway

Railway builds the `Dockerfile` straight from GitHub and redeploys on every push to `main`.

1. Sign in at [railway.com](https://railway.com) with GitHub, then **New Project -> Deploy from GitHub repo** and pick
   this repo (grant the Railway GitHub app access to just this repo).
2. In the service's **Variables** tab add (never commit these):

   | Variable | Value |
   |----------|-------|
   | `SPRING_PROFILES_ACTIVE` | `claude` |
   | `ANTHROPIC_API_KEY` | your Anthropic API key |
   | `APP_ACCESS_TOKEN` | a long random code, e.g. `openssl rand -base64 24` |

3. **Add a database** (see "Database" below), otherwise your mood history is lost on every redeploy.
4. **Settings -> Networking -> Generate Domain** to get your public `https://...up.railway.app` URL.
5. **Settings -> Deploy -> Healthcheck Path:** `/actuator/health`.
6. In Railway's billing settings set a **usage limit**, and in the Anthropic Console set a monthly spend limit.

Railway injects `PORT`, which the app already honours. Expect roughly 300 MB of memory in use. Behind Railway's proxy
all traffic may share one rate-limit bucket, which is fine for a single user; `APP_ACCESS_TOKEN` is your real protection.

---

## 📱 Use it on your iPhone

The app is a Progressive Web App. iOS requires **HTTPS** for installable web apps, so it has to be hosted somewhere your
phone can reach over HTTPS (a cloud host, or your own machine exposed privately, e.g. with Tailscale).

1. Deploy it (see Docker above) and open the HTTPS URL in **Safari** on the iPhone.
2. Tap **Share → Add to Home Screen**.
3. Launch **MoodTunes** from the home screen - it opens full screen like a native app.
4. If you set `APP_ACCESS_TOKEN`, the app opens on a **lock screen** and asks for it before showing anything else
   (see "Lock screen" below).

The app shell is cached by a service worker so it opens instantly and shows a friendly message when offline;
recommendations always need a connection because they come from the AI.

---

**After a redeploy:** the server announces its version on every response (`X-App-Version`). If the app on your phone was
open during a deploy it still has the old page in memory; it notices the change and shows a **"A new version is
available. Reload"** bar. Without it, an old page talking to a new server can fail with "Invalid request content".
Rejected requests are logged by field name (never by value), and the error message tells you which field was wrong.

---

## 🔒 Lock screen

When `APP_ACCESS_TOKEN` is set, MoodTunes asks for the access code before showing the app. Tick
**Keep me unlocked on this device** to stay signed in (the default), or untick it to be asked again each time the app
is closed and reopened. The **Lock** button forgets the code and clears the page. If the code on the server changes, the
app notices and asks again. On the iPhone, the field works with the Passwords app/Keychain autofill (not verified
inside the home-screen app). With no `APP_ACCESS_TOKEN` (local use) there is no lock screen.

The lock screen is a convenience on top of the real protection: the server rejects every `/api` call without the code
(`401`), and wrong guesses are rate limited. The page itself (HTML/JS/CSS) contains no personal data.

---

## 📈 Mood log and reports

After **Analyze**, pick up to 3 emotions that fit and tap **Save check-in**. Only the emotions are stored, never the
sentence you typed. Music for the same selection is one tap away.

The **Reports** tab covers the **last 7 days**, the **last 30 days** or the **last 13 weeks** ("3 months"), each compared
with the period just before it:

- **Summary:** check-ins, active days, and average mood with the change (▲/▼) against the previous period.
- **Mood over time:** a line from -2 (low) to +2 (high). Days without a check-in are gaps, never a zero.
- **What you felt:** emotions per day (or week) stacked by group: positive, negative, neutral and body.
- **Track an emotion:** follow up to 3 emotions (say anxious, happy, hungry) and see how often each came up.
- **Most frequent**, **recent check-ins** (with delete) and an **Export data** button that downloads everything as JSON.

Every chart has a **Table** view with the same numbers, tooltips work by hover, touch and keyboard (arrow keys), and
the colours are checked for colour-blind safety in light and dark mode.

**The emotion list is fixed** (34 words in 4 groups, see `Emotion.java`). The AI may only choose from it, so
"calm" and "calmness" can never become two lines on a graph. Each emotion has a mood score from -2 to +2; body states
(tired, hungry, sleepy, restless, sore, unwell) are tracked but do not move the mood line.

---

## 🗄️ Database

Check-ins are kept in a SQL database through Flyway migrations (`src/main/resources/db/migration`).

- **Local:** nothing to set up. A file database is created under `./data` (git-ignored) so history survives restarts.
- **Production:** set the standard Spring variables to a PostgreSQL database. The app logs which database it uses at
  startup and warns if it is still on the local file one.

  | Variable | Value |
  |----------|-------|
  | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://HOST:PORT/DATABASE` |
  | `SPRING_DATASOURCE_USERNAME` | database user |
  | `SPRING_DATASOURCE_PASSWORD` | database password |

  **Railway Postgres:** in the project choose **New -> Database -> PostgreSQL**, then on the app service use
  [variable references](https://docs.railway.com/variables) to the database service, for example
  `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` (check the service name
  matches yours). **Neon** (free) works the same way: use its host and database with `?sslmode=require`, plus its user
  and password.
- **Backups:** use **Export data** in the Reports tab now and then. Free databases have limited recovery options.

---

## 🔌 API

Stateless JSON API (errors are [RFC 7807](https://datatracker.ietf.org/doc/html/rfc7807) `application/problem+json`):

- `POST /api/analyze` - `{ "moodText": "..." }` (max 500 chars) -> `{ "emotions": ["tired", "anxious", ...] }`
  (always words from the fixed emotion list)
- `POST /api/recommend` - `{ "emotions": ["anxious", "tired"], "mediaType": "SONGS|ALBUMS" }` (1-3 emotions) ->
  `{ "items": [{ "title": "...", "artist": "...", "description": "..." }] }`
- `POST /api/checkins` - `{ "emotions": ["anxious", "tired"] }` -> `201` with `{ id, createdAt, emotions }`
- `GET /api/checkins?limit=20` - newest first; `DELETE /api/checkins/{id}` -> `204` (or `404`)
- `GET /api/reports?range=week|month|3months&tz=Europe/Dublin` - summary, per-day (or per-week) buckets and emotion
  counts; `tz` is your IANA time zone so days match your day
- `GET /api/export` - all check-ins as a JSON download
- `GET /api/auth/check` - `204` if the access code is accepted (or none is required), `401` otherwise; makes no AI call
- `GET /actuator/health` - liveness/readiness for hosting platforms

Status codes: `400` invalid input, `401` missing/wrong access token, `413` body too large, `429` rate limited
(AI calls have a tight budget because they cost money; saving and reading mood data have a generous one),
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

Covers the API contract and validation, auth/rate limiting, error handling, AI-output parsing and caching, the emotion
vocabulary, the database layer (real SQL and the Flyway migration against an in-memory database) and the report maths
(week / month / 3-month windows, time zones, gaps). No AI key is needed - the AI client is mocked.

---

## 🧠 How it works

1. The UI sends your mood text to `/api/analyze`; `MoodAnalysisService` asks the model to choose from the fixed
   emotion list and discards anything else.
2. You pick up to 3 emotions. **Save check-in** stores just those emotions (`/api/checkins`).
3. Songs or albums for the selection come from `/api/recommend`: `MusicService` asks the model for a JSON list, parses
   and validates it, and caches the parsed result (Caffeine, key = emotions + media type, 12 h, max 500 entries).
4. `ReportService` turns the saved check-ins into the weekly, monthly and 3-month reports.

---

## 📄 License

MIT - see [LICENSE](LICENSE). App icon: Twemoji 🎶 (CC-BY 4.0), see `static/favicon/about.txt`.

Built with [Spring Boot](https://spring.io/projects/spring-boot), [Spring AI](https://docs.spring.io/spring-ai/)
and [Caffeine](https://github.com/ben-manes/caffeine).
