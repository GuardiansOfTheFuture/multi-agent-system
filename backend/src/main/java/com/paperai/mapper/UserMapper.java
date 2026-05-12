package com.paperai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paperai.model.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
