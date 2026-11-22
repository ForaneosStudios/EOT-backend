package com.EOT.project.service;

import com.EOT.project.dto.response.GameTimeRecordResponseDTO;
import com.EOT.project.model.UserModel;
import com.EOT.project.repository.UserRepository;
import com.EOT.project.realtime.LiveEvent;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GameTimeRecordService {
    private final UserRepository users;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public GameTimeRecordResponseDTO get(UserModel user) {
        return GameTimeRecordResponseDTO.fromModel(user);
    }

    @Transactional
    public GameTimeRecordResponseDTO submit(UserModel user, long timeMs) {
        Long currentBest = user.getBestTimeMs();
        if (currentBest == null || timeMs < currentBest) {
            user.setBestTimeMs(timeMs);
            users.save(user);
            // Open apps refresh after the commit: the player's record and everyone's ranking.
            if (user.getAuthUserId() != null) {
                events.publishEvent(LiveEvent.toUser(user.getAuthUserId(), LiveEvent.RECORD, Map.of("bestTimeMs", timeMs)));
            }
            events.publishEvent(LiveEvent.broadcast(LiveEvent.LEADERBOARD));
        }
        return GameTimeRecordResponseDTO.fromModel(user);
    }
}
