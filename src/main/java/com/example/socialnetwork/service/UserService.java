package com.example.socialnetwork.service;

import com.example.socialnetwork.dto.CreateUserRequest;
import com.example.socialnetwork.exception.UserAlreadyExistsException;
import com.example.socialnetwork.exception.UserNotFoundException;
import com.example.socialnetwork.model.User;
import com.example.socialnetwork.model.UserMetadata;
import com.example.socialnetwork.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public Long createUser(CreateUserRequest req) {
        User user = User.builder()
                .username(req.username())
                .fullName(req.fullName())
                .age(req.age())
                .metadata(UserMetadata.of(req.metadata()))
                .build();

        Long id;
        try {
            id = userRepository.save(user);
        } catch (DuplicateKeyException e) {
            throw new UserAlreadyExistsException("User already exists", e);
        }
        return id;
    }

    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
