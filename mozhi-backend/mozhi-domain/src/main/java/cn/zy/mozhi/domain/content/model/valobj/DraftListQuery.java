package cn.zy.mozhi.domain.content.model.valobj;

import cn.zy.mozhi.types.enums.DraftStatusEnum;
import cn.zy.mozhi.types.enums.ResponseCode;
import cn.zy.mozhi.types.exception.BaseException;

public record DraftListQuery(
        int page,
        int pageSize,
        DraftStatusEnum status
) {

    public DraftListQuery {
        if (page < 1) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "page must be greater than 0");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "pageSize must be between 1 and 100");
        }
    }

    public int offset() {
        return (page - 1) * pageSize;
    }
}
