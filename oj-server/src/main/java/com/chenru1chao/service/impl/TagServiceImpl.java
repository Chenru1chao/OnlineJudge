package com.chenru1chao.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chenru1chao.entity.Tag;
import com.chenru1chao.mapper.TagMapper;
import com.chenru1chao.result.Result;
import com.chenru1chao.service.ITagService;
import com.chenru1chao.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements ITagService {

    private final TagMapper tagMapper;

    @Override
    public Result<List<TagVO>> getTagList() {
        return Result.success(tagMapper.getTagList());
    }
}
