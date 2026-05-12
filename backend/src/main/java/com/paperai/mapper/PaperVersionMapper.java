package com.paperai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.paperai.model.entity.PaperVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PaperVersionMapper extends BaseMapper<PaperVersion> {

    @Select("SELECT COALESCE(MAX(version_no), 0) + 1 FROM paper_version WHERE paper_id = #{paperId}")
    Integer nextVersionNo(@Param("paperId") Long paperId);

    @Select("SELECT * FROM paper_version WHERE paper_id = #{paperId} ORDER BY version_no DESC")
    List<PaperVersion> findByPaperId(@Param("paperId") Long paperId);

    @Select("SELECT * FROM paper_version WHERE paper_id = #{paperId} AND version_no = #{versionNo}")
    PaperVersion findByPaperIdAndVersion(@Param("paperId") Long paperId, @Param("versionNo") Integer versionNo);

    @Select("SELECT * FROM paper_version WHERE paper_id = #{paperId} ORDER BY version_no DESC LIMIT 1")
    PaperVersion findLatestByPaperId(@Param("paperId") Long paperId);
}
