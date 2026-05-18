package com.paperai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "paperai.rag")
public class RagConfig {
    private int vectorTopK = 20;
    private int keywordTopK = 10;
    private double similarityThreshold = 0.5;
    private String rerankModel = "gte-rerank";
    private int finalTopK = 5;
    private boolean hybridEnabled = true;
    private boolean rerankEnabled = true;

    public int getVectorTopK() { return vectorTopK; }
    public void setVectorTopK(int v) { this.vectorTopK = v; }
    public int getKeywordTopK() { return keywordTopK; }
    public void setKeywordTopK(int v) { this.keywordTopK = v; }
    public double getSimilarityThreshold() { return similarityThreshold; }
    public void setSimilarityThreshold(double v) { this.similarityThreshold = v; }
    public String getRerankModel() { return rerankModel; }
    public void setRerankModel(String v) { this.rerankModel = v; }
    public int getFinalTopK() { return finalTopK; }
    public void setFinalTopK(int v) { this.finalTopK = v; }
    public boolean isHybridEnabled() { return hybridEnabled; }
    public void setHybridEnabled(boolean v) { this.hybridEnabled = v; }
    public boolean isRerankEnabled() { return rerankEnabled; }
    public void setRerankEnabled(boolean v) { this.rerankEnabled = v; }
}
