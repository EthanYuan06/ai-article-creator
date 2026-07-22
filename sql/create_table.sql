# 数据库初始化（完整表结构）
# @author <a href="https://codefather.cn">编程导航学习圈</a>

-- 设置字符集（解决中文乱码问题）
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 创建库
create database if not exists ai_article_creator CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 切换库
use ai_article_creator;

-- 用户表（包含所有字段）
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    quota        int          default 5                 not null comment '剩余配额',
    vipTime      datetime                               null comment '成为会员时间',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '用户' collate = utf8mb4_unicode_ci;

-- 初始化数据
-- 密码是 12345678（MD5 加密 + 盐值为 Subaru）
INSERT INTO user (id, userAccount, userPassword, userName, userAvatar, userProfile, userRole, quota) VALUES
(1, 'subaru486', 'a4fbbc365d4988bba85fab1b8d67171e', '安和昴', 'https://yuluo-picture-1383397986.cos.ap-guangzhou.myqcloud.com/avatar/436001832335458304/1918fc1d-15fa-419e-8153-25744691820a.jpg', '一个人六七十打车回家', 'admin', 5);

-- 文章表（包含所有字段）
create table if not exists article
(
    id                    bigint auto_increment comment 'id' primary key,
    taskId                varchar(64)                        not null comment '任务ID（UUID）',
    userId                bigint                             not null comment '用户ID',
    topic                 varchar(500)                       not null comment '选题',
    userDescription       text                               null comment '用户补充描述',
    enabledImageMethods   json                               null comment '允许的配图方式列表',
    style                 varchar(20)                        null comment '文章风格：tech/emotional/educational/humorous',
    mainTitle             varchar(200)                       null comment '主标题',
    subTitle              varchar(300)                       null comment '副标题',
    titleOptions          json                               null comment '标题方案列表（3-5个方案）',
    outline               json                               null comment '大纲（JSON格式）',
    content               text                               null comment '正文（Markdown格式）',
    fullContent           text                               null comment '完整图文（Markdown格式，含配图）',
    coverImage            varchar(512)                       null comment '封面图 URL',
    images                json                               null comment '配图列表（JSON数组，包含封面图 position=1）',
    status                varchar(20) default 'PENDING'      not null comment '状态：PENDING/PROCESSING/COMPLETED/FAILED',
    phase                 varchar(50) default 'PENDING'      null comment '当前阶段：PENDING/TITLE_GENERATING/TITLE_SELECTING/OUTLINE_GENERATING/OUTLINE_EDITING/CONTENT_GENERATING',
    errorMessage          text                               null comment '错误信息',
    createTime            datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    completedTime         datetime                           null comment '完成时间',
    updateTime            datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete              tinyint     default 0              not null comment '是否删除',
    UNIQUE KEY uk_taskId (taskId),
    INDEX idx_userId (userId),
    INDEX idx_status (status),
    INDEX idx_createTime (createTime),
    INDEX idx_userId_status (userId, status)
) comment '文章表' collate = utf8mb4_unicode_ci;

-- 智能体执行日志表
create table if not exists agent_log
(
    id              bigint auto_increment comment 'id' primary key,
    taskId          varchar(64)                        not null comment '任务ID',
    agentName       varchar(50)                        not null comment '智能体名称',
    startTime       datetime                           not null comment '开始时间',
    endTime         datetime                           null comment '结束时间',
    durationMs      int                                null comment '耗时（毫秒）',
    status          varchar(20)                        not null comment '状态：SUCCESS/FAILED',
    errorMessage    text                               null comment '错误信息',
    prompt          text                               null comment '使用的Prompt',
    inputData       json                               null comment '输入数据（JSON格式）',
    outputData      json                               null comment '输出数据（JSON格式）',
    createTime      datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime      datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete        tinyint     default 0              not null comment '是否删除',
    INDEX idx_taskId (taskId),
    INDEX idx_agentName (agentName),
    INDEX idx_status (status),
    INDEX idx_createTime (createTime)
) comment '智能体执行日志表' collate = utf8mb4_unicode_ci;

-- 支付记录表
create table if not exists payment_record
(
    id                    bigint auto_increment comment '主键' primary key,
    userId                bigint                             not null comment '用户ID',
    stripeSessionId       varchar(128)                       null comment 'Stripe Checkout Session ID',
    stripePaymentIntentId varchar(128)                       null comment 'Stripe 支付意向ID',
    amount                decimal(10,2)                      not null comment '金额（美元）',
    currency              varchar(8)   default 'usd'         null comment '货币',
    status                varchar(32)                        not null comment '状态：PENDING/SUCCEEDED/FAILED/REFUNDED',
    productType           varchar(32)                        not null comment '产品类型：VIP_PERMANENT',
    description           varchar(256)                       null comment '描述',
    refundTime            datetime                           null comment '退款时间',
    refundReason          varchar(512)                       null comment '退款原因',
    createTime            datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime            datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    INDEX idx_userId (userId),
    INDEX idx_stripeSessionId (stripeSessionId),
    INDEX idx_status (status),
    INDEX idx_createTime (createTime)
) comment '支付记录表' collate = utf8mb4_unicode_ci;
