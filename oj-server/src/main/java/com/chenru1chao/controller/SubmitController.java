package com.chenru1chao.controller;

import com.chenru1chao.dto.SubmitDTO;
import com.chenru1chao.dto.SubmitPageDTO;
import com.chenru1chao.result.Result;
import com.chenru1chao.service.ISubmitService;
import com.chenru1chao.vo.PageResult;
import com.chenru1chao.vo.SubmitDetailVO;
import com.chenru1chao.vo.SubmitPageVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/submit")
@RequiredArgsConstructor
public class SubmitController {

    private final ISubmitService iSubmitService;

    @PostMapping
    public Result<SubmitDetailVO> handleUserSubmit(@RequestBody @Valid SubmitDTO submitDTO)  {
        return iSubmitService.handleUserSubmit(submitDTO);
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
