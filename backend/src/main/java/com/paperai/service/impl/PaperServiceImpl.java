package com.paperai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paperai.common.BusinessException;
import com.paperai.common.ResultCode;
import com.paperai.mapper.PaperMapper;
import com.paperai.mapper.PaperVersionMapper;
import com.paperai.mapper.TaskMapper;
import com.paperai.model.entity.Paper;
import com.paperai.model.entity.PaperVersion;
import com.paperai.model.entity.Task;
import com.paperai.model.dto.PaperWritingRequestDTO;
import com.paperai.service.PaperService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class PaperServiceImpl implements PaperService {

    @Resource
    private PaperMapper paperMapper;

    @Resource
    private PaperVersionMapper paperVersionMapper;

    @Resource
    private TaskMapper taskMapper;

    @Override
    public Paper createPaper(PaperWritingRequestDTO request, Long userId) {
        Paper paper = new Paper();
        paper.setTitle(request.getTopic());
        paper.setDescription(request.getDescription());
        paper.setKeywords(request.getKeywords());
        paper.setStatus("DRAFT");
        paper.setCurrentVersion(0);
        paper.setUserId(userId);
        paperMapper.insert(paper);
        log.info("创建论文: id={}, title={}, userId={}", paper.getId(), paper.getTitle(), userId);
        return paper;
    }

    @Override
    public Paper getPaperById(Long id) {
        Paper paper = paperMapper.selectById(id);
        if (paper == null) throw new BusinessException(ResultCode.NOT_FOUND, "论文不存在");
        return paper;
    }

    @Override
    public List<Paper> listAll() {
        return paperMapper.selectList(null);
    }

    @Override
    public List<Paper> listByUserId(Long userId) {
        return paperMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Paper>()
                        .eq(Paper::getUserId, userId)
                        .orderByDesc(Paper::getCreatedAt)
        );
    }

    @Override
    public void checkOwner(Long paperId, Long userId) {
        Paper paper = getPaperById(paperId);
        if (!paper.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该论文");
        }
    }

    @Override
    public void updateContent(Long id, Integer versionNo, String content) {
        PaperVersion pv = paperVersionMapper.findByPaperIdAndVersion(id, versionNo);
        if (pv != null) {
            pv.setContent(content);
            pv.setWordCount(content != null ? content.length() : 0);
            paperVersionMapper.updateById(pv);
        }
    }

    @Override
    public void updateStatus(Long id, String status) {
        Paper paper = getPaperById(id);
        paper.setStatus(status);
        paperMapper.updateById(paper);
    }

    @Override
    @Transactional
    public void deletePaper(Long id) {
        // 级联删除版本记录
        paperVersionMapper.delete(new LambdaQueryWrapper<PaperVersion>().eq(PaperVersion::getPaperId, id));
        // 级联删除任务记录
        taskMapper.delete(new LambdaQueryWrapper<Task>().eq(Task::getPaperId, id));
        // 删除论文本身
        paperMapper.deleteById(id);
        log.info("删除论文及关联数据: id={}", id);
    }

    // ===== 版本管理 =====

    @Override
    @Transactional
    public PaperVersion saveVersion(Long paperId, String stage, String summary, String content) {
        return saveVersion(paperId, stage, summary, content, null, null);
    }

    @Override
    @Transactional
    public PaperVersion saveVersion(Long paperId, String stage, String summary, String content,
                                    String editType, String changeSummary) {
        Integer nextNo = paperVersionMapper.nextVersionNo(paperId);

        PaperVersion pv = new PaperVersion();
        pv.setPaperId(paperId);
        pv.setVersionNo(nextNo);
        pv.setStage(stage);
        pv.setSummary(summary);
        pv.setContent(content);
        pv.setWordCount(content != null ? content.length() : 0);
        pv.setEditType(editType != null ? editType : "MANUAL");
        pv.setChangeSummary(changeSummary);
        paperVersionMapper.insert(pv);

        // 更新 paper 表 current_version
        Paper paper = getPaperById(paperId);
        paper.setCurrentVersion(nextNo);
        paperMapper.updateById(paper);

        log.info("保存论文版本: paperId={}, version={}, stage={}, editType={}, {}字",
                paperId, nextNo, stage, pv.getEditType(), pv.getWordCount());
        return pv;
    }

    @Override
    public List<PaperVersion> getVersions(Long paperId) {
        return paperVersionMapper.findByPaperId(paperId);
    }

    @Override
    public PaperVersion getVersion(Long paperId, Integer versionNo) {
        PaperVersion pv = paperVersionMapper.findByPaperIdAndVersion(paperId, versionNo);
        if (pv == null) {
            throw new BusinessException(ResultCode.PAPER_NOT_FOUND);
        }
        return pv;
    }

    @Override
    public PaperVersion getLatestVersion(Long paperId) {
        return paperVersionMapper.findLatestByPaperId(paperId);
    }
}
