create table if not exists tb_moment
(
    id          bigint auto_increment comment '主键'
        primary key,
    user_id     bigint        not null comment '发布用户ID',
    content     text          not null comment '动态内容',
    likes       int default 0 null comment '点赞数',
    comments    int default 0 null comment '评论数',
    create_time datetime      null comment '创建时间',
    update_time datetime      null comment '更新时间'
)
    comment '动态表';

create table if not exists tb_moment_like
(
    id          bigint auto_increment comment '主键'
        primary key,
    moment_id   bigint   not null comment '动态ID',
    user_id     bigint   not null comment '点赞用户ID',
    create_time datetime null comment '创建时间',
    constraint idx_moment_user
        unique (moment_id, user_id)
)
    comment '动态点赞表';

create table if not exists tb_promotion
(
    id              bigint auto_increment comment '主键'
        primary key,
    title           varchar(255)  not null comment '标题',
    description     text          null comment '描述',
    action_text     varchar(50)   null comment '按钮文字',
    action_color    varchar(50)   null comment '按钮颜色',
    type            varchar(50)   not null comment '活动类型: gift, exchange, recharge',
    required_points int default 0 null comment '兑换所需积分',
    create_time     datetime      null comment '创建时间',
    update_time     datetime      null comment '更新时间'
)
    comment '促销活动表';

create table if not exists tb_user
(
    id          bigint auto_increment comment '主键'
        primary key,
    phone       varchar(20)                       not null comment '手机号码',
    password    varchar(255)                      null comment '密码，加密存储',
    nick_name   varchar(50)                       null comment '昵称，默认是用户id',
    icon        varchar(255)                      null comment '人物头像',
    create_time datetime                          null comment '创建时间',
    update_time datetime                          null comment '更新时间',
    vip_level   int            default 0          null comment 'VIP等级',
    balance     decimal(20, 8) default 0.00000000 null comment '余额 (USDT)',
    btc_amount  decimal(20, 8) default 0.00000000 null comment '持仓 (BTC)',
    ai_chance   int            default 0          null comment 'AI交易机会',
    point       int            default 0          null comment '积分',
    constraint idx_phone
        unique (phone)
)
    comment '用户表';

create table if not exists tb_user_gift_claim
(
    id         bigint auto_increment comment '主键'
        primary key,
    user_id    bigint      not null comment '用户ID',
    gift_type  varchar(50) not null comment '礼包类型',
    claim_time datetime    null comment '领取时间',
    constraint idx_user_gift
        unique (user_id, gift_type)
)
    comment '用户礼包领取记录表';

create table if not exists ai_conversations
(
    id             bigint auto_increment comment '主键'
        primary key,
    user_id        bigint       not null comment '用户ID',
    title          varchar(255) null comment '会话标题',
    scene_type     varchar(50)  not null default 'chat' comment '场景类型: chat, strategy, advisor',
    status         varchar(50)  not null default 'active' comment '状态: active, archived',
    last_message_at datetime     null comment '最后消息时间',
    created_at     datetime     null comment '创建时间',
    updated_at     datetime     null comment '更新时间'
)
    comment 'AI会话表';

create table if not exists ai_messages
(
    id              bigint auto_increment comment '主键'
        primary key,
    conversation_id bigint       not null comment '会话ID',
    role            varchar(50)  not null comment '角色: system, user, assistant, tool',
    content         text         not null comment '消息内容',
    message_index   int          not null comment '消息序号',
    token_count     int          null comment 'token数量',
    created_at      datetime     null comment '创建时间'
)
    comment 'AI消息表';

create table if not exists ai_session_state
(
    id              bigint auto_increment comment '主键'
        primary key,
    conversation_id bigint       not null comment '会话ID',
    current_intent  varchar(100) null comment '当前意图',
    current_mode    varchar(50)  null comment '当前模式',
    current_step    varchar(100) null comment '当前步骤',
    state_json      text         null comment '状态JSON',
    updated_at      datetime     null comment '更新时间'
)
    comment 'AI会话状态表';

create table if not exists ai_conversation_summaries
(
    id                  bigint auto_increment comment '主键'
        primary key,
    conversation_id     bigint       not null comment '会话ID',
    start_message_index int          not null comment '起始消息索引',
    end_message_index   int          not null comment '结束消息索引',
    summary_text        text         not null comment '摘要内容',
    summary_type        varchar(50)  not null default 'rolling' comment '摘要类型: rolling, milestone',
    created_at          datetime     null comment '创建时间'
)
    comment 'AI会话摘要表';
