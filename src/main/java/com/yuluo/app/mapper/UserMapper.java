package com.yuluo.app.mapper;

import com.mybatisflex.core.BaseMapper;
import com.yuluo.app.model.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
