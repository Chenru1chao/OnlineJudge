package com.chenru1chao.controller;

import com.chenru1chao.dto.PageDTO;
import com.chenru1chao.result.Result;
import com.chenru1chao.service.IProblemService;
import com.chenru1chao.vo.PageResult;
import com.chenru1chao.vo.ProblemDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/problem")
@RequiredArgsConstructor
@Slf4j
public class ProblemController {

    private final IProblemService iProblemService;

    @GetMapping("/{id}")
    public Result<ProblemDetailVO> getProblemDetail(@PathVariable Integer id) {
        return iProblemService.getProblemDetail(id);
    }


    @GetMapping
    public Result<PageResult> getProblemPage(PageDTO pageDTO) {
        return iProblemService.getProblemPage(pageDTO);
    }

}
