package com.paperai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paperai.model.entity.Paper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 论文 Mapper 接口
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Mapper
public interface PaperMapper extends BaseMapper<Paper> {
}
