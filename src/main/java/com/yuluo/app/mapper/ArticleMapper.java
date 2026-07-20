package com.yuluo.app.mapper;

import com.mybatisflex.core.BaseMapper;
import com.yuluo.app.model.entity.Article;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ArticleMapper extends BaseMapper<Article> {
}
