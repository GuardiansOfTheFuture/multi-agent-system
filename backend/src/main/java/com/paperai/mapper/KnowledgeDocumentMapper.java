package com.paperai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paperai.model.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {
}
