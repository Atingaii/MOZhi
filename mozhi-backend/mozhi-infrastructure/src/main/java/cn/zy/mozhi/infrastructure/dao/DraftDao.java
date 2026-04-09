package cn.zy.mozhi.infrastructure.dao;

import cn.zy.mozhi.infrastructure.dao.po.DraftPO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DraftDao {

    int insert(DraftPO draftPO);

    int update(DraftPO draftPO);

    int deleteById(@Param("id") Long id);

    DraftPO selectById(@Param("id") Long id);

    List<DraftPO> selectByAuthorId(@Param("authorId") Long authorId);
}
