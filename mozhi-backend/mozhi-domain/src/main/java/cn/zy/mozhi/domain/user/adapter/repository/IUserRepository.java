package cn.zy.mozhi.domain.user.adapter.repository;

import cn.zy.mozhi.domain.user.model.entity.UserEntity;

import java.util.Optional;

public interface IUserRepository {

    Long save(UserEntity userEntity);

    void update(UserEntity userEntity);

    Optional<UserEntity> findById(Long userId);

    Optional<UserEntity> findByUsername(String username);

    Optional<UserEntity> findByEmail(String email);
}
