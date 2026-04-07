package cn.zy.mozhi.infrastructure.dao;

import cn.zy.mozhi.infrastructure.dao.po.UserPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserDao {

    int insert(UserPO userPO);

    int update(UserPO userPO);

    UserPO selectById(@Param("id") Long id);

    UserPO selectByUsername(@Param("username") String username);

    UserPO selectByEmail(@Param("email") String email);
}
