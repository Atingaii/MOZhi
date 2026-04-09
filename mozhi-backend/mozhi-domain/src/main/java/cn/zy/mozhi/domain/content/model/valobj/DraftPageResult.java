package cn.zy.mozhi.domain.content.model.valobj;

import cn.zy.mozhi.domain.content.model.entity.DraftEntity;

import java.util.List;

public record DraftPageResult(
        int page,
        int pageSize,
        long total,
        List<DraftEntity> items
) {
}
