package com.chenru1chao.controller;

import com.chenru1chao.result.Result;
import com.chenru1chao.service.IProblemService;
import com.chenru1chao.vo.ProblemDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
