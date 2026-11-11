package com.instakill.analytics.application;

import com.instakill.analytics.api.rest.AnalyticsResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {
    private final JdbcTemplate jdbc;

    public AnalyticsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AnalyticsResponse getAnalytics(UUID userId, double requestedTopPercent) {
        double topPercent = Double.isFinite(requestedTopPercent)
                ? Math.max(1.0, Math.min(100.0, requestedTopPercent)) : 10.0;

        List<AnalyticsResponse.WeeklyActivity> weekly = jdbc.query("""
                SELECT week, SUM(posts) AS posts, SUM(likes) AS likes, SUM(comments) AS comments
                FROM (
                    SELECT strftime('%Y-%W', created_at) AS week, COUNT(*) AS posts,
                           0 AS likes, 0 AS comments
                    FROM posts WHERE author_id = ? GROUP BY strftime('%Y-%W', created_at)
                    UNION ALL
                    SELECT strftime('%Y-%W', l.created_at) AS week, 0, COUNT(*), 0
                    FROM likes l JOIN posts p ON p.id = l.post_id
                    WHERE p.author_id = ? GROUP BY strftime('%Y-%W', l.created_at)
                    UNION ALL
                    SELECT strftime('%Y-%W', c.created_at) AS week, 0, 0, COUNT(*)
                    FROM comments c JOIN posts p ON p.id = c.post_id
                    WHERE p.author_id = ? GROUP BY strftime('%Y-%W', c.created_at)
                )
                WHERE week IS NOT NULL
                GROUP BY week ORDER BY week
                """, (rs, rowNum) -> new AnalyticsResponse.WeeklyActivity(
                rs.getString("week"), rs.getLong("posts"),
                rs.getLong("likes"), rs.getLong("comments")),
                userId.toString(), userId.toString(), userId.toString());

        List<AnalyticsResponse.TopPost> topPosts = jdbc.query("""
                SELECT p.id, p.content, p.created_at,
                       COUNT(DISTINCT l.id) AS likes,
                       COUNT(DISTINCT c.id) AS comments,
                       COUNT(DISTINCT l.id) + COUNT(DISTINCT c.id) AS engagement
                FROM posts p
                LEFT JOIN likes l ON l.post_id = p.id
                LEFT JOIN comments c ON c.post_id = p.id
                WHERE p.author_id = ?
                GROUP BY p.id, p.content, p.created_at
                ORDER BY engagement DESC, p.created_at DESC
                LIMIT 5
                """, (rs, rowNum) -> new AnalyticsResponse.TopPost(
                rs.getString("id"), rs.getString("content"), rs.getString("created_at"),
                rs.getLong("likes"), rs.getLong("comments"), rs.getLong("engagement")),
                userId.toString());

        long likes = jdbc.queryForObject("""
                SELECT COUNT(*) FROM likes l JOIN posts p ON p.id = l.post_id
                WHERE p.author_id = ?
                """, Long.class, userId.toString());
        long comments = jdbc.queryForObject("""
                SELECT COUNT(*) FROM comments c JOIN posts p ON p.id = c.post_id
                WHERE p.author_id = ?
                """, Long.class, userId.toString());
        Double ratio = comments == 0 ? null : (double) likes / comments;

        List<AnalyticsResponse.MediaTypeAverage> mediaAverages = jdbc.query("""
                SELECT CASE
                         WHEN lower(image_url) LIKE '%.mp4%' OR lower(image_url) LIKE '%.mov%'
                           OR lower(image_url) LIKE '%.webm%' THEN 'video'
                         ELSE 'photo'
                       END AS media_type,
                       COUNT(*) AS post_count,
                       AVG(COALESCE(like_totals.likes, 0)) AS average_likes
                FROM posts p
                LEFT JOIN (
                    SELECT post_id, COUNT(*) AS likes FROM likes GROUP BY post_id
                ) like_totals ON like_totals.post_id = p.id
                WHERE p.author_id = ? AND p.image_url IS NOT NULL AND trim(p.image_url) <> ''
                GROUP BY media_type ORDER BY media_type
                """, (rs, rowNum) -> new AnalyticsResponse.MediaTypeAverage(
                rs.getString("media_type"), rs.getLong("post_count"),
                rs.getDouble("average_likes")), userId.toString());

        AnalyticsResponse.EngagementRanking ranking = jdbc.queryForObject("""
                WITH post_engagement AS (
                    SELECT p.id, p.author_id,
                           (SELECT COUNT(*) FROM likes l WHERE l.post_id = p.id) +
                           (SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id) AS engagement
                    FROM posts p
                ),
                user_averages AS (
                    SELECT u.id, COALESCE(AVG(pe.engagement), 0.0) AS avg_engagement
                    FROM users u LEFT JOIN post_engagement pe ON pe.author_id = u.id
                    GROUP BY u.id
                ),
                selected_user AS (
                    SELECT avg_engagement FROM user_averages WHERE id = ?
                )
                SELECT COALESCE((SELECT avg_engagement FROM selected_user), 0.0) AS user_average,
                       (SELECT COUNT(*) FROM user_averages ua
                        WHERE ua.avg_engagement >= (SELECT avg_engagement FROM selected_user)) AS user_rank,
                       (SELECT COUNT(*) FROM user_averages) AS total_users
                """, (rs, rowNum) -> {
                    long rank = rs.getLong("user_rank");
                    long total = rs.getLong("total_users");
                    double userAverage = rs.getDouble("user_average");
                    double actualTopPercent = total == 0 ? 100.0 : (double) rank / total * 100.0;
                    return new AnalyticsResponse.EngagementRanking(
                            userAverage, rank, total, topPercent,
                            total > 0 && actualTopPercent <= topPercent);
                }, userId.toString());

        return new AnalyticsResponse(weekly, topPosts,
                new AnalyticsResponse.InteractionRatio(likes, comments, ratio),
                mediaAverages, ranking);
    }
}
