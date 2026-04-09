package cn.zy.mozhi.infrastructure.dao;

import cn.zy.mozhi.infrastructure.dao.po.MediaRefPO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface MediaRefDao {

    int insert(MediaRefPO mediaRefPO);

    MediaRefPO selectByStorageIdentity(@Param("storageProvider") String storageProvider,
                                       @Param("bucketName") String bucketName,
                                       @Param("objectKey") String objectKey);

    List<MediaRefPO> selectByDraftId(@Param("draftId") Long draftId);
}
