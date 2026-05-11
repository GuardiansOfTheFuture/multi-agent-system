package com.paperai.service;

import com.paperai.model.entity.Paper;
import com.paperai.model.dto.PaperWritingRequestDTO;
import com.paperai.model.vo.PaperWritingVO;

import java.util.List;

/**
 * 论文服务接口
 *
 * @author: ch
 * @date 2026年05月11日
 */
public interface PaperService {

    /**
     * 创建论文记录
     */
    Paper createPaper(PaperWritingRequestDTO request);

    /**
     * 根据ID查询论文
     */
    Paper getPaperById(Long id);

    /**
     * 查询所有论文列表
     */
    List<Paper> listAll();

    /**
     * 更新论文内容
     */
    void updateContent(Long id, String content);

    /**
     * 更新论文状态
     */
    void updateStatus(Long id, String status);

    /**
     * 删除论文
     */
    void deletePaper(Long id);
}
