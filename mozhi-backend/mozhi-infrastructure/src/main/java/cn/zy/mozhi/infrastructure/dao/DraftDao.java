package cn.zy.mozhi.infrastructure.dao;

import cn.zy.mozhi.infrastructure.dao.po.DraftPO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DraftDao {

    int insert(DraftPO draftPO);

    int update(DraftPO draftPO);

    int deleteById(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion);

    DraftPO selectById(@Param("id") Long id);

    List<DraftPO> selectPageByAuthorId(@Param("authorId") Long authorId,
                                       @Param("status") String status,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    long countByAuthorId(@Param("authorId") Long authorId, @Param("status") String status);
}
