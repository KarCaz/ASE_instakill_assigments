# Instakill

Instakill is a Spring Boot 3 / Vue 3 starter for an Instagram-style social feed. The project starts as a monolith but is structured for future extraction into independent services.

## Project layout

- `backend/` – Spring Boot application exposing REST and MVC endpoints, backed by SQLite.
- `frontend/` – Vue 3 SPA that consumes the REST API.
- `scripts/` – helper tooling including Gradle bootstrap.

## First run

Make sure you have Java 23 installed.

```bash
# From the project root
./scripts/bootstrap-gradle.sh
./backend/gradlew bootRun
```

On Windows PowerShell:

```powershell
./scripts/bootstrap-gradle.ps1
./backend/gradlew.bat bootRun
```

The bootstrap downloads Gradle 8.9 and generates a local wrapper (ignored by git). Subsequent builds can use the wrapper directly.

## Useful endpoints

- Web feed: http://localhost:8080/feed
- Login page: http://localhost:8080/auth/login
- REST OpenAPI UI: http://localhost:8080/api/swagger
- Health: http://localhost:8080/actuator/health
- Media upload: `POST /api/media` (multipart field `file`, authenticated) returns `{"mediaUrl": "/media/<uuid>.<ext>"}`; files are stored in `media.base-path` (default `./media`) and served from `/media/**`

## Post notifications

Every new post triggers an asynchronous HTTP request to a public [ntfy.sh](https://ntfy.sh) topic with the username, post ID and timestamp. The notification is sent after the post is committed, on a background thread, so it never slows down or breaks post creation. Subscribe by opening `https://ntfy.sh/<topic>` (or the ntfy app). Configure it in `application.yaml` under `notification.ntfy` (`enabled`, `base-url`, `topic`); the topic can also be set with the `NTFY_TOPIC` environment variable. Topics on ntfy.sh are public, so choose a unique name.

## Image processing

Uploaded `jpg`, `jpeg`, `png` and `gif` images are processed before saving: scaled down to at most 300 px wide (aspect ratio kept, small images are not enlarged) and stamped with an "Instakill Demo" watermark in the bottom-right corner. Settings live under `media.processing`. The logic is isolated in `com.instakill.media.processing` behind the `ImageProcessor` interface (bytes in, bytes out), so it can later move to a separate service or background worker. GIFs are saved as a single frame; videos and `webp` files are stored unchanged.

## Authentication

Authentication uses JWT stored in an `ACCESS_TOKEN` HttpOnly cookie. REST clients can authenticate by capturing the cookie returned from `/api/auth/login` or by sending the token manually.

Seed accounts (development profile):

| Username | Email             | Password |
|----------|-------------------|----------|
| alice    | alice@example.com | password |
| bob      | bob@example.com   | password |

## Running tests

```bash
cd backend
./gradlew test
```

The tests cover feed ranking, like toggling, MVC flows, and an end-to-end REST happy-path.

## Frontend

The Vue application lives in `frontend/`. Install dependencies and start the dev server with:

```bash
cd frontend
npm install
npm run dev
```

With default settings, you will be able to access the frontend on `http://localhost:5173/`
It assumes the backend is running at `http://localhost:8080`.
Check `vite.config.js` for these settings.


## Week 5 – User analytics

After logging in, select **My analytics** in the header. The analytics endpoint is
`GET /api/analytics?topPercent=10` and requires the authenticated `ACCESS_TOKEN`
cookie. It returns:

- Weekly posts created, likes received, and comments received (grouped by event week).
- The five posts with the highest total likes plus comments.
- The user's overall like-to-comment ratio (`N/A` in the UI when there are no comments).
- Average likes per photo and video post. Text-only posts are excluded from this comparison.
- The user's average engagement rank against all registered users, including users with no posts.
  The top-percent selector supports 5%, 10%, 25%, and 50%.

The statistics are aggregated in SQLite queries rather than loading all posts and
interactions into application memory.

## Week 5 – Generate a larger SQLite dataset

Install the Python dependencies:

```bash
python -m pip install Faker bcrypt
```

Start the backend once so Hibernate creates `backend/instakill.db`, then stop it.
From the repository root, run:

```bash
python scripts/populate_database.py
```

By default this targets 10,000 total users and creates 10–20 posts per user,
0–20 unique likes per post, and 0–10 comments per post. It clears existing
posts, likes, and comments, keeps the `alice` and `bob` accounts, and creates
(or resets) `charlie`. All three seed accounts use the password `password`.
Generated content and post/interactions timestamps are placed within the past
seven days. The operation is committed as one SQLite transaction, with inserts
executed in batches.

Options:

```bash
python scripts/populate_database.py --users 1000
python scripts/populate_database.py --db path/to/instakill.db --users 5000 --seed 42
```

**Warning:** this script intentionally replaces the current posts, likes, and
comments in the selected database. Back up the database first if you need its data.
