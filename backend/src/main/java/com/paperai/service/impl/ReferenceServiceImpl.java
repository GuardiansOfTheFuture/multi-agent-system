package com.paperai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.paperai.mapper.ReferenceMapper;
import com.paperai.model.entity.Reference;
import com.paperai.service.PaperService;
import com.paperai.service.ReferenceService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ReferenceServiceImpl implements ReferenceService {

    @Resource private ReferenceMapper referenceMapper;
    @Resource private PaperService paperService;

    @Override
    public List<Reference> listByPaperId(Long paperId) {
        return referenceMapper.selectList(
                new LambdaQueryWrapper<Reference>()
                        .eq(Reference::getPaperId, paperId)
                        .orderByAsc(Reference::getCreatedAt));
    }

    @Override
    public Reference getById(Long id) {
        return referenceMapper.selectById(id);
    }

    @Override
    public Reference add(Long paperId, Reference ref) {
        ref.setPaperId(paperId);
        if (ref.getCited() == null) ref.setCited(0);
        if (ref.getType() == null) ref.setType("other");
        referenceMapper.insert(ref);
        return ref;
    }

    @Override
    public Reference update(Long id, Reference ref, Long userId) {
        Reference existing = referenceMapper.selectById(id);
        if (existing == null) throw new RuntimeException("参考文献不存在");
        paperService.checkOwner(existing.getPaperId(), userId);
        ref.setId(id);
        referenceMapper.updateById(ref);
        return referenceMapper.selectById(id);
    }

    @Override
    public void delete(Long id, Long userId) {
        Reference existing = referenceMapper.selectById(id);
        if (existing == null) throw new RuntimeException("参考文献不存在");
        paperService.checkOwner(existing.getPaperId(), userId);
        referenceMapper.deleteById(id);
    }

    @Override
    public int importBibtex(Long paperId, String bibtexText) {
        List<Reference> refs = parseBibtex(bibtexText);
        int count = 0;
        for (Reference ref : refs) {
            ref.setPaperId(paperId);
            if (ref.getCited() == null) ref.setCited(0);
            if (ref.getType() == null) ref.setType("other");
            referenceMapper.insert(ref);
            count++;
        }
        return count;
    }

    @Override
    public List<Reference> extractFromResearchOutput(Long paperId, String researchOutput) {
        if (researchOutput == null || researchOutput.isBlank()) return List.of();
        List<Reference> refs = new ArrayList<>();
        int refsIdx = researchOutput.indexOf("### 5. 参考文献");
        if (refsIdx == -1) refsIdx = researchOutput.indexOf("### 5.");
        if (refsIdx == -1) refsIdx = researchOutput.indexOf("**参考文献**");
        if (refsIdx == -1) return refs;

        String refsSection = researchOutput.substring(refsIdx);
        String[] lines = refsSection.split("\n");
        for (String line : lines) {
            String trimmed = line.replaceFirst("^[-*\\d][.、]?\\s*", "").trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            Reference ref = new Reference();
            ref.setPaperId(paperId);
            ref.setRawText(trimmed);
            ref.setType("other");
            ref.setCited(0);
            ref.setTitle(extractTitle(trimmed));
            ref.setAuthors(extractAuthors(trimmed));
            ref.setYear(extractYear(trimmed));
            refs.add(ref);
        }
        for (Reference ref : refs) {
            referenceMapper.insert(ref);
        }
        return refs;
    }

    // ---- BibTeX parsing ----

    private List<Reference> parseBibtex(String text) {
        List<Reference> refs = new ArrayList<>();
        Pattern p = Pattern.compile("@(\\w+)\\s*\\{\\s*([^,]+),\\s*([^}]+)\\}", Pattern.DOTALL);
        Matcher m = p.matcher(text);
        while (m.find()) {
            String type = m.group(1).toLowerCase();
            String fields = m.group(3);
            Reference ref = new Reference();
            ref.setType(mapBibtexType(type));
            ref.setTitle(fieldValue(fields, "title"));
            ref.setAuthors(fieldValue(fields, "author"));
            ref.setYear(parseIntSafe(fieldValue(fields, "year")));
            ref.setJournal(fieldValue(fields, "journal"));
            if (ref.getJournal() == null) ref.setJournal(fieldValue(fields, "booktitle"));
            ref.setVolume(fieldValue(fields, "volume"));
            ref.setIssue(fieldValue(fields, "number"));
            ref.setPages(fieldValue(fields, "pages"));
            ref.setDoi(fieldValue(fields, "doi"));
            ref.setUrl(fieldValue(fields, "url"));
            ref.setRawText(buildCitationText(ref));
            ref.setCited(0);
            refs.add(ref);
        }
        if (refs.isEmpty()) {
            refs.addAll(parsePlainLines(text));
        }
        return refs;
    }

    private List<Reference> parsePlainLines(String text) {
        List<Reference> refs = new ArrayList<>();
        for (String line : text.split("\n")) {
            String trimmed = line.replaceFirst("^[-*\\d][.、]?\\s*", "").trim();
            if (trimmed.isEmpty() || trimmed.length() < 5) continue;
            Reference ref = new Reference();
            ref.setRawText(trimmed);
            ref.setType("other");
            ref.setTitle(extractTitle(trimmed));
            ref.setAuthors(extractAuthors(trimmed));
            ref.setYear(extractYear(trimmed));
            ref.setCited(0);
            refs.add(ref);
        }
        return refs;
    }

    private String fieldValue(String fields, String key) {
        Pattern p = Pattern.compile(key + "\\s*=\\s*[\"\\{]([^\"\\}]+)[\"\\}]", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(fields);
        return m.find() ? m.group(1).trim() : null;
    }

    private Integer parseIntSafe(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); } catch (NumberFormatException e) { return null; }
    }

    private String mapBibtexType(String bibType) {
        return switch (bibType) {
            case "article" -> "journal";
            case "inproceedings", "conference" -> "conference";
            case "book" -> "book";
            default -> "other";
        };
    }

    private String buildCitationText(Reference ref) {
        StringBuilder sb = new StringBuilder();
        if (ref.getAuthors() != null) sb.append(ref.getAuthors());
        if (ref.getYear() != null) sb.append(" (").append(ref.getYear()).append(")");
        if (ref.getTitle() != null) sb.append(". ").append(ref.getTitle());
        if (ref.getJournal() != null) sb.append(". ").append(ref.getJournal());
        if (ref.getVolume() != null) sb.append(", ").append(ref.getVolume());
        if (ref.getIssue() != null) sb.append("(").append(ref.getIssue()).append(")");
        if (ref.getPages() != null) sb.append(", ").append(ref.getPages());
        if (ref.getDoi() != null) sb.append(". DOI:").append(ref.getDoi());
        return sb.toString();
    }

    private String extractTitle(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("\\(\\d{4}[a-z]?\\).*$", "").trim();
        if (s.length() > 200) s = s.substring(0, 200);
        return s;
    }

    private String extractAuthors(String raw) {
        if (raw == null || raw.length() < 3) return null;
        int parenIdx = raw.indexOf("(");
        if (parenIdx > 0) {
            String authorPart = raw.substring(0, parenIdx).trim();
            authorPart = authorPart.replaceAll(",\\s*(\\d{4}).*$", "").trim();
            if (authorPart.length() > 100) authorPart = authorPart.substring(0, 100);
            return authorPart;
        }
        return null;
    }

    private Integer extractYear(String raw) {
        if (raw == null) return null;
        Pattern p = Pattern.compile("\\((\\d{4})[a-z]?\\)");
        Matcher m = p.matcher(raw);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
