package com.chenru1chao.controller;

import com.chenru1chao.dto.SubmitDTO;
import com.chenru1chao.result.Result;
import com.chenru1chao.service.ISubmitService;
import com.chenru1chao.vo.SubmitResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/submit")
@RequiredArgsConstructor
@Slf4j
public class SubmitController {

    private final ISubmitService iSubmitService;

    @PostMapping
    public Result<SubmitResultVO> handleUserSubmit(@RequestBody SubmitDTO submitDTO)  {
        return iSubmitService.handleUserSubmit(submitDTO);
    }
}
