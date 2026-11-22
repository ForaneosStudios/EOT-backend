package com.EOT.project.realtime;

import com.EOT.project.model.UserModel;
import com.EOT.project.repository.UserRepository;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public ranking of the fastest runs. Exposes only public profile data (no emails). */
@RestController
@RequestMapping("/api/v1/leaderboard")
public class LeaderboardController {
    static final int MAX_LIMIT = 50;
    private final UserRepository users;

    public LeaderboardController(UserRepository users) {
        this.users = users;
    }

    public record Entry(int rank, String username, String profilePicture, long bestTimeMs) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<Entry> top(@RequestParam(defaultValue = "10") int limit) {
        int size = Math.clamp(limit, 1, MAX_LIMIT);
        List<UserModel> fastest = users.findByIsDeletedFalseAndBestTimeMsNotNullOrderByBestTimeMsAscCreatedAtAsc(PageRequest.of(0, size));
        return IntStream.range(0, fastest.size())
                .mapToObj(i -> new Entry(i + 1, fastest.get(i).getUsername(), fastest.get(i).getProfilePicture(), fastest.get(i).getBestTimeMs()))
                .toList();
    }
}
