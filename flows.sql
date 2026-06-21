-- ============================================================
-- 预设流程 SQL（插入 flow_definition 表）
-- user_id = 1，请根据实际用户 ID 修改
-- ============================================================

-- 1. 标准流程
INSERT INTO flow_definition (user_id, name, description, category, graph_data, is_template, created_at, updated_at)
VALUES (
  1,
  '标准流程',
  '选题评估 - 文献调研 - 大纲 - 写作 - 审稿 - 润色 - 终审',
  'preset',
  JSON_OBJECT(
    'nodes', JSON_ARRAY(
      JSON_OBJECT('id','n1','type','agent','position',JSON_OBJECT('x',80,'y',60),'data',JSON_OBJECT('agentRole','SUPERVISOR','label','选题评估','roleName','导师','stepIndex',1,'config',JSON_OBJECT('systemPrompt','评估研究选题的学术价值、创新性和可行性','model','mimo-v2.5-pro','temperature',0.5,'timeout',120,'retryCount',2,'notes',''),'status','pending')),
      JSON_OBJECT('id','n2','type','agent','position',JSON_OBJECT('x',80,'y',200),'data',JSON_OBJECT('agentRole','RESEARCHER','label','文献调研','roleName','研究员','stepIndex',2,'config',JSON_OBJECT('systemPrompt','深入调研相关领域文献，提取关键发现和研究趋势','model','mimo-v2.5-pro','temperature',0.3,'timeout',180,'retryCount',2,'notes',''),'status','pending')),
      JSON_OBJECT('id','n3','type','agent','position',JSON_OBJECT('x',80,'y',340),'data',JSON_OBJECT('agentRole','WRITER','label','生成大纲','roleName','写作者','stepIndex',3,'config',JSON_OBJECT('systemPrompt','根据调研结果生成论文大纲','model','mimo-v2.5-pro','temperature',0.7,'timeout',120,'retryCount',2,'notes',''),'status','pending')),
      JSON_OBJECT('id','n4','type','agent','position',JSON_OBJECT('x',80,'y',480),'data',JSON_OBJECT('agentRole','WRITER','label','分章撰写','roleName','写作者','stepIndex',4,'config',JSON_OBJECT('systemPrompt','按大纲逐章撰写论文正文','model','mimo-v2.5-pro','temperature',0.7,'timeout',300,'retryCount',2,'notes',''),'status','pending')),
      JSON_OBJECT('id','n5','type','agent','position',JSON_OBJECT('x',80,'y',620),'data',JSON_OBJECT('agentRole','REVIEWER','label','审稿评审','roleName','审稿人','stepIndex',5,'config',JSON_OBJECT('systemPrompt','从创新性、方法学、逻辑性、表达质量四个维度审阅论文','model','mimo-v2.5-pro','temperature',0.6,'timeout',180,'retryCount',2,'notes',''),'status','pending')),
      JSON_OBJECT('id','n6','type','agent','position',JSON_OBJECT('x',80,'y',760),'data',JSON_OBJECT('agentRole','POLISHER','label','润色定稿','roleName','润色师','stepIndex',6,'config',JSON_OBJECT('systemPrompt','对论文进行语言润色、格式规范检查','model','mimo-v2.5-pro','temperature',0.4,'timeout',120,'retryCount',2,'notes',''),'status','pending')),
      JSON_OBJECT('id','n7','type','agent','position',JSON_OBJECT('x',80,'y',900),'data',JSON_OBJECT('agentRole','SUPERVISOR','label','最终审核','roleName','导师','stepIndex',7,'config',JSON_OBJECT('systemPrompt','对论文进行最终审核，给出综合评价','model','mimo-v2.5-pro','temperature',0.5,'timeout',120,'retryCount',2,'notes',''),'status','pending'))
    ),
    'edges', JSON_ARRAY(
      JSON_OBJECT('id','e1','source','n1','target','n2','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal')),
      JSON_OBJECT('id','e2','source','n2','target','n3','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal')),
      JSON_OBJECT('id','e3','source','n3','target','n4','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal')),
      JSON_OBJECT('id','e4','source','n4','target','n5','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal')),
      JSON_OBJECT('id','e5','source','n5','target','n6','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal')),
      JSON_OBJECT('id','e6','source','n6','target','n7','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal'))
    )
  ),
  1,
  NOW(),
  NOW()
);


-- 2. 快速草稿
INSERT INTO flow_definition (user_id, name, description, category, graph_data, is_template, created_at, updated_at)
VALUES (
  1,
  '快速草稿',
  '调研 - 大纲 - 写作 - 润色 - 终审（跳过选题评估和审稿迭代）',
  'preset',
  JSON_OBJECT(
    'nodes', JSON_ARRAY(
      JSON_OBJECT('id','n1','type','agent','position',JSON_OBJECT('x',80,'y',100),'data',JSON_OBJECT('agentRole','RESEARCHER','label','文献调研','roleName','研究员','stepIndex',1,'config',JSON_OBJECT('systemPrompt','快速调研相关文献，提取核心信息','model','mimo-v2.5-pro','temperature',0.3,'timeout',120,'retryCount',2,'notes',''),'status','pending')),
      JSON_OBJECT('id','n2','type','agent','position',JSON_OBJECT('x',80,'y',250),'data',JSON_OBJECT('agentRole','WRITER','label','生成大纲','roleName','写作者','stepIndex',2,'config',JSON_OBJECT('systemPrompt','快速生成论文大纲','model','mimo-v2.5-pro','temperature',0.7,'timeout',60,'retryCount',2,'notes',''),'status','pending')),
      JSON_OBJECT('id','n3','type','agent','position',JSON_OBJECT('x',80,'y',400),'data',JSON_OBJECT('agentRole','WRITER','label','撰写论文','roleName','写作者','stepIndex',3,'config',JSON_OBJECT('systemPrompt','快速撰写论文各章节','model','mimo-v2.5-pro','temperature',0.7,'timeout',300,'retryCount',2,'notes',''),'status','pending')),
      JSON_OBJECT('id','n4','type','agent','position',JSON_OBJECT('x',80,'y',550),'data',JSON_OBJECT('agentRole','POLISHER','label','润色','roleName','润色师','stepIndex',4,'config',JSON_OBJECT('systemPrompt','语言润色和格式规范','model','mimo-v2.5-pro','temperature',0.4,'timeout',60,'retryCount',2,'notes',''),'status','pending')),
      JSON_OBJECT('id','n5','type','agent','position',JSON_OBJECT('x',80,'y',700),'data',JSON_OBJECT('agentRole','SUPERVISOR','label','终审','roleName','导师','stepIndex',5,'config',JSON_OBJECT('systemPrompt','最终审核并给出结论','model','mimo-v2.5-pro','temperature',0.5,'timeout',60,'retryCount',2,'notes',''),'status','pending'))
    ),
    'edges', JSON_ARRAY(
      JSON_OBJECT('id','e1','source','n1','target','n2','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal')),
      JSON_OBJECT('id','e2','source','n2','target','n3','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal')),
      JSON_OBJECT('id','e3','source','n3','target','n4','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal')),
      JSON_OBJECT('id','e4','source','n4','target','n5','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal'))
    )
  ),
  1,
  NOW(),
  NOW()
);


-- 3. 深度研究
INSERT INTO flow_definition (user_id, name, description, category, graph_data, is_template, created_at, updated_at)
VALUES (
  1,
  '深度研究',
  '选题评估 - 文献调研 - 大纲 - 写作 - 审稿x5 - 润色 - 终审（适合期刊论文）',
  'preset',
  JSON_OBJECT(
    'nodes', JSON_ARRAY(
      JSON_OBJECT('id','n1','type','agent','position',JSON_OBJECT('x',80,'y',60),'data',JSON_OBJECT('agentRole','SUPERVISOR','label','选题评估','roleName','导师','stepIndex',1,'config',JSON_OBJECT('systemPrompt','评估研究选题的学术价值、创新性和可行性','model','mimo-v2.5-pro','temperature',0.5,'timeout',120,'retryCount',2,'notes',''),'status','pending')),
      JSON_OBJECT('id','n2','type','agent','position',JSON_OBJECT('x',80,'y',200),'data',JSON_OBJECT('agentRole','RESEARCHER','label','深度调研','roleName','研究员','stepIndex',2,'config',JSON_OBJECT('systemPrompt','深入系统地调研相关领域文献，分析研究趋势和空白','model','mimo-v2.5-pro','temperature',0.3,'timeout',240,'retryCount',2,'notes','需要大量检索'),'status','pending')),
      JSON_OBJECT('id','n3','type','agent','position',JSON_OBJECT('x',80,'y',340),'data',JSON_OBJECT('agentRole','WRITER','label','生成大纲','roleName','写作者','stepIndex',3,'config',JSON_OBJECT('systemPrompt','根据调研结果生成详细的论文大纲','model','mimo-v2.5-pro','temperature',0.7,'timeout',120,'retryCount',2,'notes',''),'status','pending')),
      JSON_OBJECT('id','n4','type','agent','position',JSON_OBJECT('x',80,'y',480),'data',JSON_OBJECT('agentRole','WRITER','label','分章撰写','roleName','写作者','stepIndex',4,'config',JSON_OBJECT('systemPrompt','按大纲逐章撰写高质量论文正文','model','mimo-v2.5-pro','temperature',0.7,'timeout',300,'retryCount',2,'notes','核心写作步骤'),'status','pending')),
      JSON_OBJECT('id','n5','type','agent','position',JSON_OBJECT('x',300,'y',480),'data',JSON_OBJECT('agentRole','REVIEWER','label','审稿评审','roleName','审稿人','stepIndex',5,'config',JSON_OBJECT('systemPrompt','从创新性、方法学、逻辑性、表达质量四个维度深入审阅','model','mimo-v2.5-pro','temperature',0.6,'timeout',180,'retryCount',2,'notes',''),'status','pending')),
      JSON_OBJECT('id','n6','type','condition','position',JSON_OBJECT('x',300,'y',340),'data',JSON_OBJECT('label','质量判断','stepIndex',6,'config',JSON_OBJECT('condition','output.contains(严重问题) || score < 7','notes','检查审稿意见是否有严重问题'),'status','pending')),
      JSON_OBJECT('id','n7','type','agent','position',JSON_OBJECT('x',520,'y',340),'data',JSON_OBJECT('agentRole','WRITER','label','修改论文','roleName','写作者','stepIndex',7,'config',JSON_OBJECT('systemPrompt','根据审稿意见修改论文，重点解决严重问题','model','mimo-v2.5-pro','temperature',0.7,'timeout',180,'retryCount',2,'notes',''),'status','pending')),
      JSON_OBJECT('id','n8','type','loop','position',JSON_OBJECT('x',520,'y',480),'data',JSON_OBJECT('label','审稿循环','stepIndex',8,'config',JSON_OBJECT('maxIterations',5,'notes','最多循环5次'),'status','pending')),
      JSON_OBJECT('id','n9','type','agent','position',JSON_OBJECT('x',300,'y',620),'data',JSON_OBJECT('agentRole','POLISHER','label','润色定稿','roleName','润色师','stepIndex',9,'config',JSON_OBJECT('systemPrompt','对论文进行语言润色、格式规范检查','model','mimo-v2.5-pro','temperature',0.4,'timeout',120,'retryCount',2,'notes',''),'status','pending')),
      JSON_OBJECT('id','n10','type','agent','position',JSON_OBJECT('x',300,'y',760),'data',JSON_OBJECT('agentRole','SUPERVISOR','label','最终审核','roleName','导师','stepIndex',10,'config',JSON_OBJECT('systemPrompt','对论文进行最终审核，给出综合评价','model','mimo-v2.5-pro','temperature',0.5,'timeout',120,'retryCount',2,'notes',''),'status','pending'))
    ),
    'edges', JSON_ARRAY(
      JSON_OBJECT('id','e1','source','n1','target','n2','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal')),
      JSON_OBJECT('id','e2','source','n2','target','n3','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal')),
      JSON_OBJECT('id','e3','source','n3','target','n4','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal')),
      JSON_OBJECT('id','e4','source','n4','target','n5','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal')),
      JSON_OBJECT('id','e5','source','n5','target','n6','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal')),
      JSON_OBJECT('id','e6','source','n6','target','n7','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,90,90,0.4)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,90,90,0.5)','width',16,'height',16),'data',JSON_OBJECT('label','不通过','conditionType','failure')),
      JSON_OBJECT('id','e7','source','n6','target','n9','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(82,196,26,0.4)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(82,196,26,0.5)','width',16,'height',16),'data',JSON_OBJECT('label','通过','conditionType','success')),
      JSON_OBJECT('id','e8','source','n7','target','n8','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal')),
      JSON_OBJECT('id','e9','source','n8','target','n5','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(114,46,209,0.4)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(114,46,209,0.5)','width',16,'height',16),'data',JSON_OBJECT('label','回退','conditionType','loop')),
      JSON_OBJECT('id','e10','source','n9','target','n10','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal'))
    )
  ),
  1,
  NOW(),
  NOW()
);


-- 4. 纯写作
INSERT INTO flow_definition (user_id, name, description, category, graph_data, is_template, created_at, updated_at)
VALUES (
  1,
  '纯写作',
  '写作 - 润色 - 终审（已有素材，跳过调研和审稿）',
  'preset',
  JSON_OBJECT(
    'nodes', JSON_ARRAY(
      JSON_OBJECT('id','n1','type','agent','position',JSON_OBJECT('x',80,'y',100),'data',JSON_OBJECT('agentRole','WRITER','label','撰写论文','roleName','写作者','stepIndex',1,'config',JSON_OBJECT('systemPrompt','根据已有素材撰写论文','model','mimo-v2.5-pro','temperature',0.7,'timeout',300,'retryCount',2,'notes',''),'status','pending')),
      JSON_OBJECT('id','n2','type','agent','position',JSON_OBJECT('x',80,'y',250),'data',JSON_OBJECT('agentRole','POLISHER','label','润色','roleName','润色师','stepIndex',2,'config',JSON_OBJECT('systemPrompt','语言润色和格式规范','model','mimo-v2.5-pro','temperature',0.4,'timeout',60,'retryCount',2,'notes',''),'status','pending')),
      JSON_OBJECT('id','n3','type','agent','position',JSON_OBJECT('x',80,'y',400),'data',JSON_OBJECT('agentRole','SUPERVISOR','label','终审','roleName','导师','stepIndex',3,'config',JSON_OBJECT('systemPrompt','最终审核并给出结论','model','mimo-v2.5-pro','temperature',0.5,'timeout',60,'retryCount',2,'notes',''),'status','pending'))
    ),
    'edges', JSON_ARRAY(
      JSON_OBJECT('id','e1','source','n1','target','n2','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal')),
      JSON_OBJECT('id','e2','source','n2','target','n3','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal'))
    )
  ),
  1,
  NOW(),
  NOW()
);


-- 5. 综述论文
INSERT INTO flow_definition (user_id, name, description, category, graph_data, is_template, created_at, updated_at)
VALUES (
  1,
  '综述论文',
  '调研 - 大纲 - 写作 - 润色 - 终审（适合文献综述类论文）',
  'preset',
  JSON_OBJECT(
    'nodes', JSON_ARRAY(
      JSON_OBJECT('id','n1','type','agent','position',JSON_OBJECT('x',80,'y',100),'data',JSON_OBJECT('agentRole','RESEARCHER','label','深度调研','roleName','研究员','stepIndex',1,'config',JSON_OBJECT('systemPrompt','系统性地调研领域文献，梳理研究脉络和发展趋势','model','mimo-v2.5-pro','temperature',0.3,'timeout',240,'retryCount',2,'notes','综述核心步骤'),'status','pending')),
      JSON_OBJECT('id','n2','type','agent','position',JSON_OBJECT('x',80,'y',250),'data',JSON_OBJECT('agentRole','WRITER','label','生成大纲','roleName','写作者','stepIndex',2,'config',JSON_OBJECT('systemPrompt','根据调研结果生成综述论文大纲','model','mimo-v2.5-pro','temperature',0.7,'timeout',120,'retryCount',2,'notes',''),'status','pending')),
      JSON_OBJECT('id','n3','type','agent','position',JSON_OBJECT('x',80,'y',400),'data',JSON_OBJECT('agentRole','WRITER','label','撰写综述','roleName','写作者','stepIndex',3,'config',JSON_OBJECT('systemPrompt','撰写综述论文，重点梳理文献脉络、对比分析、指出研究空白','model','mimo-v2.5-pro','temperature',0.7,'timeout',300,'retryCount',2,'notes',''),'status','pending')),
      JSON_OBJECT('id','n4','type','agent','position',JSON_OBJECT('x',80,'y',550),'data',JSON_OBJECT('agentRole','POLISHER','label','润色','roleName','润色师','stepIndex',4,'config',JSON_OBJECT('systemPrompt','语言润色和格式规范','model','mimo-v2.5-pro','temperature',0.4,'timeout',60,'retryCount',2,'notes',''),'status','pending')),
      JSON_OBJECT('id','n5','type','agent','position',JSON_OBJECT('x',80,'y',700),'data',JSON_OBJECT('agentRole','SUPERVISOR','label','终审','roleName','导师','stepIndex',5,'config',JSON_OBJECT('systemPrompt','最终审核并给出结论','model','mimo-v2.5-pro','temperature',0.5,'timeout',60,'retryCount',2,'notes',''),'status','pending'))
    ),
    'edges', JSON_ARRAY(
      JSON_OBJECT('id','e1','source','n1','target','n2','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal')),
      JSON_OBJECT('id','e2','source','n2','target','n3','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal')),
      JSON_OBJECT('id','e3','source','n3','target','n4','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal')),
      JSON_OBJECT('id','e4','source','n4','target','n5','type','smoothstep','animated',true,'style',JSON_OBJECT('stroke','rgba(255,255,255,0.18)','strokeWidth',1.5),'markerEnd',JSON_OBJECT('type','arrowclosed','color','rgba(255,255,255,0.3)','width',16,'height',16),'data',JSON_OBJECT('label','','conditionType','normal'))
    )
  ),
  1,
  NOW(),
  NOW()
);
