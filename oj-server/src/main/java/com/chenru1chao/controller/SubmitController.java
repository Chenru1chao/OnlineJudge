package com.chenru1chao.controller;

import com.chenru1chao.dto.SubmitDTO;
import com.chenru1chao.dto.SubmitPageDTO;
import com.chenru1chao.result.Result;
import com.chenru1chao.service.ISubmitService;
import com.chenru1chao.vo.HeatmapCellVO;
import com.chenru1chao.vo.PageResult;
import com.chenru1chao.vo.SubmitDetailVO;
import com.chenru1chao.vo.SubmitPageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/submit")
@RequiredArgsConstructor
public class SubmitController {

    private final ISubmitService iSubmitService;

    @PostMapping
    public Result<SubmitDetailVO> handleUserSubmit(@RequestBody @Valid SubmitDTO submitDTO)  {
        return iSubmitService.handleUserSubmit(submitDTO);
    }

    // 字面量路径优先于 /{id} 匹配，不会被 getSubmitStatus 抢走
    @GetMapping("/heatmap")
    public Result<List<HeatmapCellVO>> getHeatmap(@RequestParam(required = false) Integer userId,
                                                  @RequestParam(required = false, defaultValue = "365") Integer days) {
        return iSubmitService.getHeatmap(userId, days);
    }

    @GetMapping("/{id}")
    public Result<SubmitDetailVO> getSubmitStatus(@PathVariable Integer id) {
        return iSubmitService.getSubmitStatus(id);
    }

    @GetMapping
    public Result<PageResult<SubmitPageVO>> getSubmitPage(SubmitPageDTO submitPageDTO) {
        return iSubmitService.getSubmitPage(submitPageDTO);
    }
}
