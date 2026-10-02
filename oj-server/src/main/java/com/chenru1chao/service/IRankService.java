package com.chenru1chao.service;

import com.chenru1chao.dto.RankPageDTO;
import com.chenru1chao.result.Result;
import com.chenru1chao.vo.PageResult;
import com.chenru1chao.vo.RankVO;

public interface IRankService {
    Result<PageResult<RankVO>> getUserRank(RankPageDTO rankPageDTO);
}
