-- 第二阶段：长期记忆 + 统一 RAG

create table if not exists ai_user_memories
(
    id                bigint auto_increment comment '主键'
        primary key,
    user_id           bigint       not null comment '用户ID',
    memory_type       varchar(50)  not null comment '记忆类型: preference, goal, constraint, profile, project_context',
    content           text         not null comment '记忆内容',
    importance_score  decimal(3,2) default 1.00 comment '重要性评分 0.00-1.00',
    confidence_score  decimal(3,2) default 1.00 comment '置信度评分 0.00-1.00',
    source            varchar(255) null comment '来源',
    is_active         tinyint(1)   default 1 comment '是否有效',
    last_used_at      datetime     null comment '最后使用时间',
    created_at        datetime     null comment '创建时间',
    updated_at        datetime     null comment '更新时间'
)
    comment 'AI用户长期记忆表';

create table if not exists ai_memory_embeddings
(
    id              bigint auto_increment comment '主键'
        primary key,
    memory_id       bigint       not null comment '记忆ID',
    embedding_ref   varchar(255) not null comment '向量引用(Redis key)',
    embedding_model varchar(100) null comment '使用的embedding模型',
    created_at      datetime     null comment '创建时间'
)
    comment 'AI记忆向量表';

create table if not exists ai_knowledge_docs
(
    id          bigint auto_increment comment '主键'
        primary key,
    user_id     bigint       null comment '用户ID（知识库按用户隔离）',
    doc_type    varchar(50)  not null comment '文档类型: strategy, term, rule, research',
    title       varchar(255) not null comment '文档标题',
    source      varchar(255) null comment '来源',
    status      varchar(50)  default 'active' comment '状态: active, archived',
    created_at  datetime     null comment '创建时间',
    updated_at  datetime     null comment '更新时间'
)
    comment 'AI知识文档表';

create table if not exists ai_knowledge_chunks
(
    id            bigint auto_increment comment '主键'
        primary key,
    doc_id        bigint       not null comment '文档ID',
    chunk_index   int          not null comment '分片序号',
    chunk_text    text         not null comment '分片内容',
    keywords      varchar(500) null comment '关键词',
    embedding_ref varchar(255) null comment '向量引用',
    created_at    datetime     null comment '创建时间'
)
    comment 'AI知识分片表';

create table if not exists ai_context_logs
(
    id                    bigint auto_increment comment '主键'
        primary key,
    conversation_id       bigint       not null comment '会话ID',
    user_message_id       bigint       null comment '用户消息ID',
    scene_type            varchar(50)  null comment '场景类型',
    used_summary_ids      varchar(500) null comment '使用的摘要ID列表',
    used_memory_ids       varchar(500) null comment '使用的记忆ID列表',
    used_knowledge_ids    varchar(500) null comment '使用的知识ID列表',
    retrieval_score_avg   decimal(5,4) null comment '平均检索分数',
    prompt_token_estimate int          null comment '预估prompt token数',
    trim_action           varchar(255) null comment '裁剪动作',
    validation_status     varchar(50)  null comment '验证状态',
    created_at            datetime     null comment '创建时间'
)
    comment 'AI上下文日志表';