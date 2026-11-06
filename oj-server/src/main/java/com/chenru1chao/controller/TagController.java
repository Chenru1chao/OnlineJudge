package com.chenru1chao.controller;

import com.chenru1chao.result.Result;
import com.chenru1chao.service.ITagService;
import com.chenru1chao.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TagController {

    private final ITagService iTagService;

    @GetMapping("/tag/list")
    public Result<List<TagVO>> getTagList() {
        return iTagService.getTagList();
    }
}
