package com.paperai.service.impl;

import com.paperai.common.BusinessException;
import com.paperai.common.ResultCode;
import com.paperai.mapper.PaperMapper;
import com.paperai.model.entity.Paper;
import com.paperai.model.dto.PaperWritingRequestDTO;
import com.paperai.service.PaperService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 论文服务实现
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Slf4j
@Service
public class PaperServiceImpl implements PaperService {

    @Resource
    private PaperMapper paperMapper;

    @Override
    public Paper createPaper(PaperWritingRequestDTO request) {
        Paper paper = new Paper();
        paper.setTitle(request.getTopic());
        paper.setDescription(request.getDescription());
        paper.setKeywords(request.getKeywords());
        paper.setStatus("DRAFT");
        paperMapper.insert(paper);
        log.info("创建论文: id={}, title={}", paper.getId(), paper.getTitle());
        return paper;
    }

    @Override
    public Paper getPaperById(Long id) {
        Paper paper = paperMapper.selectById(id);
        if (paper == null) {
            throw new BusinessException(ResultCode.PAPER_NOT_FOUND);
        }
        return paper;
    }

    @Override
    public List<Paper> listAll() {
        return paperMapper.selectList(null);
    }

    @Override
    public void updateContent(Long id, String content) {
        Paper paper = getPaperById(id);
        paper.setContent(content);
        paperMapper.updateById(paper);
    }

    @Override
    public void updateStatus(Long id, String status) {
        Paper paper = getPaperById(id);
        paper.setStatus(status);
        paperMapper.updateById(paper);
    }

    @Override
    public void deletePaper(Long id) {
        paperMapper.deleteById(id);
    }
}
