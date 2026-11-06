package com.chenru1chao.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chenru1chao.entity.Tag;
import com.chenru1chao.result.Result;
import com.chenru1chao.vo.TagVO;

import java.util.List;

public interface ITagService extends IService<Tag> {
    Result<List<TagVO>> getTagList();
}
