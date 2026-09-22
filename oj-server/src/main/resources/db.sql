create database if not exists online_judge;

use online_judge;

create table if not exists `user` (
    id int primary key auto_increment comment '用户编号',
    avatar varchar(40) comment '用户头像',
    username varchar(20) not null comment '用户昵称',
    password varchar(50) not null default('123') comment '用户密码',
    age int check (age > 0 and age < 200) comment '用户年龄',
    gender varchar(5) check (gender = '男' or gender = '女') comment '用户性别',
    mood varchar(100) comment '个性签名',
    total_submit int default 0 comment '累计提交',
    total_accept int default 0 comment '累计ac量'
) charset=utf8mb4;

create table if not exists user_info (
    user_id int not null unique comment '用户id',
    real_name varchar(20) comment '真实名称',
    phone varchar(11) unique check (length(phone) = 11) comment '用户手机号',
    github varchar(30) comment 'github地址',
    email varchar(30) comment '邮箱地址',
    school varchar(20) comment '用户院校',
    major varchar(20) comment '主修专业',
    create_time datetime default now() comment '创建时间'
) charset=utf8mb4;

create table if not exists problem (
    id int primary key auto_increment comment '题目编号',
    title varchar(100) not null comment '题目名称',
    `input` text not null comment '输入',
    `output` text not null comment '输出',
    description text not null comment '题目描述',
    difficulty int not null check (difficulty >= 1 and difficulty <= 5) comment '题目难度1-5',
    time_limit int not null comment '时间限制(ms)',
    memory_limit int not null comment '内存限制(mb)',
    submit_total int not null default 0 comment '提交总数',
    pass_total int not null default 0 comment '通过总数',
    author varchar(20) not null default 'chenru1chao' comment '题目贡献者',
    create_time datetime default now() comment '创建时间'
) charset=utf8mb4;

create table if not exists tag(
    id int primary key auto_increment comment '标签编号',
    tag_info varchar(20) not null unique comment '标签信息'
) charset=utf8mb4;

create table if not exists problem_tag (
    id int primary key auto_increment comment '标签编号',
    tag_id int not null comment '标签标签',
    problem_id int not null comment '题目编号'
) charset=utf8mb4;

create table if not exists submit(
    id int primary key auto_increment comment '提交编号',
    user_id int not null comment '用户编号',
    problem_id int not null comment '题目编号',
    status int not null default 1 comment '提交状态',
    failed_case_no int comment '第一个失败的用例序号，AC 为 null',
    error_msg text comment '编译/运行时错误信息，其余为 null',
    submit_language varchar(10) comment '语言类型',
    code text not null comment '提交代码',
    time_used int comment '运行时间(ms)',
    memory_used int comment '运行内存(kb)',
    submit_time datetime default now() comment '提交时间'
) charset=utf8mb4;

create table if not exists problem_sample (
    id int primary key auto_increment comment '提示用例编号',
    problem_id int not null comment '题目编号',
    input varchar(100) not null comment '输入文本',
    output varchar(100) not null comment '输出文本',
    sort int not null comment '排序字段'
) charset=utf8mb4;

create table if not exists problem_test_case (
    id int primary key auto_increment comment '测试用例编号',
    problem_id int not null comment '题目编号',
    input varchar(100) not null comment '输入文本',
    output varchar(100) not null comment '输出文本',
    sort int not null comment '排序字段'
) charset=utf8mb4;
