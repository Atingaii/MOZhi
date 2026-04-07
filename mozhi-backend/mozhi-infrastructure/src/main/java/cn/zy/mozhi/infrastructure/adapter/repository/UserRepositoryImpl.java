package cn.zy.mozhi.infrastructure.adapter.repository;

import cn.zy.mozhi.domain.user.adapter.repository.IUserRepository;
import cn.zy.mozhi.domain.user.model.entity.UserEntity;
import cn.zy.mozhi.infrastructure.dao.UserDao;
import cn.zy.mozhi.infrastructure.dao.po.UserPO;
import cn.zy.mozhi.types.enums.UserStatusEnum;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@ConditionalOnBean(UserDao.class)
public class UserRepositoryImpl implements IUserRepository {

    private final UserDao userDao;

    public UserRepositoryImpl(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public Long save(UserEntity userEntity) {
        UserPO userPO = toPO(userEntity);
        userDao.insert(userPO);
        return userPO.getId();
    }

    @Override
    public void update(UserEntity userEntity) {
        userDao.update(toPO(userEntity));
    }

    @Override
    public Optional<UserEntity> findById(Long userId) {
        return Optional.ofNullable(userDao.selectById(userId)).map(this::toEntity);
    }

    @Override
    public Optional<UserEntity> findByUsername(String username) {
        return Optional.ofNullable(userDao.selectByUsername(username)).map(this::toEntity);
    }

    @Override
    public Optional<UserEntity> findByEmail(String email) {
        return Optional.ofNullable(userDao.selectByEmail(email)).map(this::toEntity);
    }

    private UserPO toPO(UserEntity userEntity) {
        UserPO userPO = new UserPO();
        userPO.setId(userEntity.getId());
        userPO.setUsername(userEntity.getUsername());
        userPO.setEmail(userEntity.getEmail());
        userPO.setPasswordHash(userEntity.getPasswordHash());
        userPO.setNickname(userEntity.getNickname());
        userPO.setAvatarUrl(userEntity.getAvatarUrl());
        userPO.setBio(userEntity.getBio());
        userPO.setStatus(userEntity.getStatus().name());
        userPO.setCreatedAt(userEntity.getCreatedAt());
        userPO.setUpdatedAt(userEntity.getUpdatedAt());
        return userPO;
    }

    private UserEntity toEntity(UserPO userPO) {
        return new UserEntity(
                userPO.getId(),
                userPO.getUsername(),
                userPO.getEmail(),
                userPO.getPasswordHash(),
                userPO.getNickname(),
                userPO.getAvatarUrl(),
                userPO.getBio(),
                UserStatusEnum.valueOf(userPO.getStatus()),
                userPO.getCreatedAt(),
                userPO.getUpdatedAt()
        );
    }
}
