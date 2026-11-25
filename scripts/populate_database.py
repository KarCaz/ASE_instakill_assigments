#!/usr/bin/env python3
"""
Populate the Instakill SQLite database with reproducible, relationally valid demo data.

The script keeps the existing alice and bob accounts, creates charlie/password,
removes all existing posts/likes/comments, and fills the database to --users users.
"""
from __future__ import annotations

import argparse
import random
import sqlite3
import uuid
from datetime import date, datetime, time, timedelta, timezone
from pathlib import Path

import bcrypt
from faker import Faker


def iso_timestamp(fake: Faker, start: date, end: date) -> str:
    day = fake.date_between_dates(date_start=start, date_end=end)
    day_start = datetime.combine(day, time.min, tzinfo=timezone.utc)
    now = datetime.now(timezone.utc)
    latest_second = min(86_399, max(0, int((now - day_start).total_seconds())))
    moment = day_start + timedelta(seconds=random.randint(0, latest_second))
    return moment.isoformat(timespec="seconds").replace("+00:00", "Z")


def make_password_hash(password: str) -> str:
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def existing_columns(connection: sqlite3.Connection, table: str) -> set[str]:
    return {row[1] for row in connection.execute(f"PRAGMA table_info({table})")}


def require_schema(connection: sqlite3.Connection) -> None:
    expected = {
        "users": {"id", "username", "email", "password_hash", "created_at"},
        "posts": {"id", "author_id", "content", "image_url", "created_at",
                  "updated_at", "like_count", "comment_count"},
        "likes": {"id", "post_id", "user_id", "created_at"},
        "comments": {"id", "post_id", "author_id", "content", "created_at"},
    }
    for table, columns in expected.items():
        actual = existing_columns(connection, table)
        missing = columns - actual
        if missing:
            raise RuntimeError(
                f"Table '{table}' is missing columns: {', '.join(sorted(missing))}. "
                "Start the Spring Boot application once so Hibernate can initialize the schema."
            )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--db", default=None,
                        help="SQLite database path (auto-detects ./instakill.db or ./backend/instakill.db)")
    parser.add_argument("--users", type=int, default=10_000,
                        help="Target total number of users (default: 10000)")
    parser.add_argument("--seed", type=int, default=None,
                        help="Optional random seed for repeatable generated data")
    args = parser.parse_args()
    if args.users < 3:
        parser.error("--users must be at least 3 (alice, bob, and charlie)")

    random.seed(args.seed)
    fake = Faker()
    if args.seed is not None:
        Faker.seed(args.seed)

    if args.db:
        db_path = Path(args.db).expanduser()
    else:
        candidates = (Path("instakill.db"), Path("backend/instakill.db"))
        db_path = next((candidate for candidate in candidates if candidate.is_file()), candidates[0])
    if not db_path.is_file():
        raise FileNotFoundError(
            f"Database not found: {db_path}. Run the backend once to create it, "
            "or pass the correct path with --db."
        )

    connection = sqlite3.connect(db_path)
    connection.execute("PRAGMA foreign_keys = ON")
    try:
        require_schema(connection)
        now = datetime.now(timezone.utc)
        week_start = (now - timedelta(days=7)).date()
        today = now.date()
        password_hash = make_password_hash("password")

        with connection:
            # Delete children before parents to respect the foreign-key constraints.
            connection.execute("DELETE FROM comments")
            connection.execute("DELETE FROM likes")
            connection.execute("DELETE FROM posts")
            connection.execute("DELETE FROM users WHERE username NOT IN ('alice', 'bob')")

            users_by_name = {
                row[0]: row[1]
                for row in connection.execute(
                    "SELECT username, id FROM users WHERE username IN ('alice', 'bob')"
                )
            }

            # Keep alice/bob if present; create either account if this is a fresh database.
            for username in ("alice", "bob"):
                if username not in users_by_name:
                    user_id = str(uuid.uuid4())
                    connection.execute(
                        """INSERT INTO users (id, username, email, password_hash, created_at)
                           VALUES (?, ?, ?, ?, ?)""",
                        (user_id, username, f"{username}@example.com",
                         password_hash, now.isoformat(timespec="seconds").replace("+00:00", "Z"))
                    )
                    users_by_name[username] = user_id

            charlie_row = connection.execute(
                "SELECT id FROM users WHERE username = 'charlie'"
            ).fetchone()
            if charlie_row:
                charlie_id = charlie_row[0]
                connection.execute(
                    "UPDATE users SET password_hash = ? WHERE id = ?",
                    (password_hash, charlie_id)
                )
            else:
                charlie_id = str(uuid.uuid4())
                connection.execute(
                    """INSERT INTO users (id, username, email, password_hash, created_at)
                       VALUES (?, 'charlie', 'charlie@example.com', ?, ?)""",
                    (charlie_id, password_hash,
                     now.isoformat(timespec="seconds").replace("+00:00", "Z"))
                )
            users_by_name["charlie"] = charlie_id

            current_count = connection.execute("SELECT COUNT(*) FROM users").fetchone()[0]
            generated_users = []
            used_usernames = {row[0] for row in connection.execute("SELECT username FROM users")}
            for _ in range(max(0, args.users - current_count)):
                username = fake.unique.user_name()[:30]
                while username in used_usernames:
                    username = fake.unique.user_name()[:30]
                used_usernames.add(username)
                # Faker's unique registry handles usernames; email gets a UUID suffix as a
                # second guard against collisions with preserved seed accounts.
                email = f"{username}.{uuid.uuid4().hex[:8]}@example.test"
                user_id = str(uuid.uuid4())
                generated_users.append((
                    user_id, username, email, password_hash,
                    iso_timestamp(fake, week_start, today)
                ))
            connection.executemany(
                """INSERT INTO users (id, username, email, password_hash, created_at)
                   VALUES (?, ?, ?, ?, ?)""",
                generated_users
            )

            all_users = [row[0] for row in connection.execute("SELECT id FROM users")]
            post_owners = []
            post_rows = []
            for user_id in all_users:
                for _ in range(random.randint(10, 20)):
                    post_id = str(uuid.uuid4())
                    created_at = iso_timestamp(fake, week_start, today)
                    if random.choice(("photo", "video")) == "video":
                        image_url = "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"
                    else:
                        image_url = f"https://picsum.photos/seed/{uuid.uuid4().hex}/640/480.jpg"
                    content = " ".join(fake.words(nb=random.randint(2, 10)))[:1000]
                    post_rows.append((
                        post_id, user_id, content, image_url, created_at,
                        created_at, 0, 0
                    ))
                    post_owners.append((post_id, user_id))
                    if len(post_rows) >= 5000:
                        connection.executemany(
                            """INSERT INTO posts
                               (id, author_id, content, image_url, created_at, updated_at,
                                like_count, comment_count)
                               VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                            post_rows
                        )
                        post_rows.clear()
            if post_rows:
                connection.executemany(
                    """INSERT INTO posts
                       (id, author_id, content, image_url, created_at, updated_at,
                        like_count, comment_count)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                    post_rows
                )

            # Sample unique likers by index without building a 10,000-user list per post.
            user_index = {user_id: index for index, user_id in enumerate(all_users)}
            def interactions():
                like_batch = []
                comment_batch = []
                for post_id, owner_id in post_owners:
                    like_count = random.randint(0, 20)
                    owner_index = user_index[owner_id]
                    available = len(all_users) - 1
                    for selected_index in random.sample(range(available), min(like_count, available)):
                        actual_index = selected_index if selected_index < owner_index else selected_index + 1
                        like_batch.append((
                            str(uuid.uuid4()), post_id, all_users[actual_index],
                            iso_timestamp(fake, week_start, today)
                        ))
                    for _ in range(random.randint(0, 10)):
                        comment_batch.append((
                            str(uuid.uuid4()), post_id, random.choice(all_users),
                            " ".join(fake.words(nb=random.randint(2, 10)))[:500],
                            iso_timestamp(fake, week_start, today)
                        ))
                    if len(like_batch) >= 5000:
                        connection.executemany(
                            "INSERT INTO likes (id, post_id, user_id, created_at) VALUES (?, ?, ?, ?)",
                            like_batch
                        )
                        like_batch.clear()
                    if len(comment_batch) >= 5000:
                        connection.executemany(
                            "INSERT INTO comments (id, post_id, author_id, content, created_at) VALUES (?, ?, ?, ?, ?)",
                            comment_batch
                        )
                        comment_batch.clear()
                if like_batch:
                    connection.executemany(
                        "INSERT INTO likes (id, post_id, user_id, created_at) VALUES (?, ?, ?, ?)",
                        like_batch
                    )
                if comment_batch:
                    connection.executemany(
                        "INSERT INTO comments (id, post_id, author_id, content, created_at) VALUES (?, ?, ?, ?, ?)",
                        comment_batch
                    )
            interactions()

            # Keep the denormalized counters in posts consistent with the interaction rows.
            connection.execute("""
                UPDATE posts SET like_count = (
                    SELECT COUNT(*) FROM likes WHERE likes.post_id = posts.id
                ), comment_count = (
                    SELECT COUNT(*) FROM comments WHERE comments.post_id = posts.id
                )
            """)

        print("Database populated successfully.")
        print(f"Database: {db_path.resolve()}")
        print(f"Users: {connection.execute('SELECT COUNT(*) FROM users').fetchone()[0]:,}")
        print(f"Posts: {connection.execute('SELECT COUNT(*) FROM posts').fetchone()[0]:,}")
        print(f"Likes: {connection.execute('SELECT COUNT(*) FROM likes').fetchone()[0]:,}")
        print(f"Comments: {connection.execute('SELECT COUNT(*) FROM comments').fetchone()[0]:,}")
        print("Login accounts: alice / bob / charlie (password: password)")
    finally:
        connection.close()


if __name__ == "__main__":
    main()
