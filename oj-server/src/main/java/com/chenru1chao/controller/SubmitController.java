package com.chenru1chao.controller;

import com.chenru1chao.dto.SubmitDTO;
import com.chenru1chao.result.Result;
import com.chenru1chao.service.ISubmitService;
import com.chenru1chao.vo.SubmitVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/submit")
@RequiredArgsConstructor
public class SubmitController {

    private final ISubmitService iSubmitService;

    @PostMapping
    public Result<SubmitVO> handleUserSubmit(@RequestBody SubmitDTO submitDTO)  {
        return iSubmitService.handleUserSubmit(submitDTO);
    }

    @GetMapping("/{id}")
    public Result<SubmitVO> getSubmitStatus(@PathVariable Integer id) {
        return iSubmitService.getSubmitStatus(id);
    }
}
