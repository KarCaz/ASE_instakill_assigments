package com.instakill.analytics.api.rest;

import java.util.List;

public record AnalyticsResponse(
        List<WeeklyActivity> weeklyActivity,
        List<TopPost> topPosts,
        InteractionRatio interactionRatio,
        List<MediaTypeAverage> averageLikesByMediaType,
        EngagementRanking engagementRanking
) {
    public record WeeklyActivity(String week, long posts, long likes, long comments) {}
    public record TopPost(String postId, String content, String createdAt,
                          long likes, long comments, long engagement) {}
    public record InteractionRatio(long likes, long comments, Double likeToCommentRatio) {}
    public record MediaTypeAverage(String mediaType, long posts, double averageLikes) {}
    public record EngagementRanking(double userAverageEngagement, long rank,
                                    long totalUsers, double topPercent,
                                    boolean inTopPercent) {}
}
