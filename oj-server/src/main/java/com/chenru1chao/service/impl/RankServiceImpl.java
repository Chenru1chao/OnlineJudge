package com.chenru1chao.service.impl;

import com.chenru1chao.dto.RankPageDTO;
import com.chenru1chao.mapper.UserMapper;
import com.chenru1chao.result.Result;
import com.chenru1chao.service.IRankService;
import com.chenru1chao.vo.PageResult;
import com.chenru1chao.vo.RankVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RankServiceImpl implements IRankService {

    private final UserMapper userMapper;

    @Override
    public Result<PageResult<RankVO>> getUserRank(RankPageDTO rankPageDTO) {
        Integer pageNO = rankPageDTO.getPageNO();
        Integer pageSize = rankPageDTO.getPageSize();

        Integer begin = Math.max((pageNO - 1) * pageSize, 0);

        List<RankVO> rankVOs = userMapper.getUserRank(begin, pageSize);

        Integer nums = userMapper.getTotalUser();

        PageResult<RankVO> result = new PageResult<RankVO>(nums, rankPageDTO.getPageNO(),
                rankPageDTO.getPageSize(), rankVOs);

        return Result.success(result);
    }
}
