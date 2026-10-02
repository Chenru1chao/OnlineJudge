package com.chenru1chao.controller;

import com.chenru1chao.dto.RankPageDTO;
import com.chenru1chao.result.Result;
import com.chenru1chao.service.IRankService;
import com.chenru1chao.vo.PageResult;
import com.chenru1chao.vo.RankVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rank")
@RequiredArgsConstructor
public class RankController {

    private final IRankService iRankService;

    @GetMapping
    public Result<PageResult<RankVO>> getUserRank(RankPageDTO rankPageDTO) {
        return iRankService.getUserRank(rankPageDTO);
    }
}
