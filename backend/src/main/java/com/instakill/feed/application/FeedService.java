
package com.instakill.feed.application;

import com.instakill.feed.domain.FeedRankingStrategy;
import com.instakill.feed.domain.FeedSort;
import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FeedService {

    private final PostRepository posts;
    private final Map<FeedSort, FeedRankingStrategy> strategies;

    public FeedService(
            PostRepository posts,
            List<FeedRankingStrategy> rankingStrategies) {

        this.posts = posts;

        this.strategies = rankingStrategies.stream()
                .collect(Collectors.toMap(
                        FeedRankingStrategy::sort,
                        Function.identity()
                ));
    }

    public List<Post> listFeed(
            FeedSort sort,
            int page,
            int size) {

        FeedSort requestedSort =
                sort == null ? FeedSort.NEW : sort;

        FeedRankingStrategy strategy =
                strategies.get(requestedSort);

        if (strategy == null) {
            throw new IllegalArgumentException(
                    "Unsupported feed sort: " + requestedSort
            );
        }

        return strategy.fetch(posts, page, size);
    }
}
