package com.EOT.project.service;

import com.EOT.project.dto.response.GameTimeRecordResponseDTO;
import com.EOT.project.model.UserModel;
import com.EOT.project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GameTimeRecordService {
    private final UserRepository users;

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
        }
        return GameTimeRecordResponseDTO.fromModel(user);
    }
}
