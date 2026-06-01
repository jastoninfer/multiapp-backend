-- 数据库初始化
create schema if not exists app;
-- TENANT
-- 由于tenant是超级管理员才有修改权限, 只考虑创建请求的幂等实现
-- 对于读写并发的乐观锁控制, 暂不考虑
create table tenant(
    id uuid primary key,
    name text not null,
    status text not null default 'ACTIVE'
        check (status in ('ACTIVE', 'SUSPENDED')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create unique index uq_tenant_name_ci on tenant (lower(btrim(name)));

-- APP_USER[仅Keycloak注册用户本地数据]
-- app_user的创建显然也需要幂等操作
-- app_user的修改, 并入platform admin|tenant admin修改status
-- 但由于这是低频的操作, 暂不考虑加入version字段控制并发
-- 但创建/修改需要进audit_log
create table app_user(
    id uuid primary key,
    issuer text not null,
    keycloak_sub text not null,
    email text not null, -- 注册用户, 邮箱是必选项
    display_name text not null, -- 注册用户, 用户名也必须提供
    phone text default null, -- 注册用户, phone是可选项
    status text not null default 'ACTIVE'
        check(status in ('ACTIVE', 'DISABLED')),
    is_platform_admin boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_user_issuer_sub unique (issuer, keycloak_sub)
);
create index ix_user_email on app_user(email);

-- TENANT_MEMBERSHIP 租户内角色
-- role可能会被并发修改, 增加version字段支持乐观锁
create table tenant_membership(
      tenant_id uuid not null,
      user_id uuid not null,
      version bigint not null default 0,
      role text not null check(role in
                               ('ADMIN', 'AGENT', 'RESOURCE_USER', 'CUSTOMER')
          ),
      is_default boolean not null default false, --是选择该租户作为默认租户
      created_at timestamptz not null default now(),
      primary key (tenant_id, user_id),
      constraint fk_membership_tenant
          foreign key (tenant_id) references tenant(id),
      constraint fk_membership_user
          foreign key (user_id) references app_user(id)
);
-- 同一user最多一个默认租户(partial unique index)
create unique index uq_membership_one_default_per_user
    on tenant_membership(user_id)
    where is_default = true;

-- CONTACT[租户内外部联系人档案, 不保证和APP_USER同步]
-- contact信息可能会被并发修改, 增加version字段支持乐观锁
-- 可以考虑对unique(tenant_id, email/normailized_email)或
-- unique(tenant_id, phone/normalized_phone)进行去重, 但目前不考虑实现
-- contact不属于核心业务表
create table contact(
    tenant_id uuid not null,
    id uuid not null,
    version bigint not null default 0,
    contact_type text not null default 'PERSON'
        check(contact_type in ('PERSON','ORG')),
    email text default null, -- cant be ''
    phone text default null, -- cant be ''
    email_normalized text default null,
    phone_normalized text default null,
    display_name text not null, -- cant be ''
    linked_user_id uuid default null,
    created_by_user_id uuid not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (tenant_id, id),
    -- email/phone至少一个非空
    constraint ck_contact_identity_nonempty check(
        email is not null or phone is not null),
    constraint fk_contact_linked_membership
        foreign key (tenant_id, linked_user_id)
        references tenant_membership(tenant_id, user_id),
    constraint fk_contact_created_by
        foreign key (created_by_user_id)
        references app_user(id)
);
create index ix_contact_tenant_email_norm
    on contact(tenant_id, email_normalized);
create index ix_contact_tenant_phone_norm
    on contact(tenant_id, phone_normalized);
-- 部分唯一索引: 同一租户下, email/phone都应该是唯一的
create unique index uq_contact_tenant_email_norm
    on contact(tenant_id, email_normalized)
    where email_normalized is not null;
create unique index uq_contact_tenant_phone_norm
    on contact(tenant_id, phone_normalized)
    where phone_normalized is not null;

-- TICKET 工单
-- 工单可能会被并发修改(PATCH), 增加version字段支持乐观锁
create sequence ticket_no_seq start with 1;
create table ticket(
    tenant_id uuid not null,
    id uuid not null,
    ticket_no bigint not null default nextval('ticket_no_seq'),
    version bigint not null default 0, -- 乐观锁
    requester_user_id uuid default null,
    requester_contact_id uuid default null,
    created_by_user_id uuid not null,
    owner_user_id uuid default null,
    status text not null check(status in
        ('NEW','IN_PROGRESS','CLOSED','REOPENED')
    ),
    priority text not null check(priority in
        ('LOW','MEDIUM','HIGH','URGENT')
    ),
    ticket_type text not null check(ticket_type in
        ('INCIDENT','SERVICE_REQUEST')
    ),
    title text not null,
    description text,
    location_text text,
    first_response_at timestamptz,
    closed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (tenant_id, id),
    constraint uq_ticket_tenant_no unique (tenant_id, ticket_no),
    -- requester_user_id与requester_contact_id必须且只能填一个
    constraint ck_ticket_requester_xor check(
        (requester_contact_id is not null) <> (requester_user_id is not null)
    ),
    constraint fk_ticket_creator_membership
        foreign key (tenant_id, created_by_user_id)
        references tenant_membership (tenant_id, user_id),
    constraint fk_ticket_requester_user_membership
        foreign key (tenant_id, requester_user_id)
        references tenant_membership (tenant_id, user_id),
    constraint fk_ticket_requester_contact_membership
        foreign key (tenant_id, requester_contact_id)
        references contact (tenant_id, id),
    constraint fk_ticket_owner_membership
        foreign key (tenant_id, owner_user_id)
        references tenant_membership (tenant_id, user_id)
);
create index ix_ticket_tenant_status_updated
    on ticket (tenant_id, status, updated_at desc);
create index ix_ticket_tenant_owner_status
    on ticket (tenant_id, owner_user_id, status);
create index ix_ticket_tenant_requester_user_created
    on ticket (tenant_id, requester_user_id, created_at desc);
create index ix_ticket_tenant_requester_contact
    on ticket (tenant_id, requester_contact_id);

-- CONTACT_CLAIM 认领机制单独表
-- 同一个claim_code被两个人同时提交; 必须保证只有一个能消费成功
-- 乐观锁但不需要增加version字段支持, 尝试消费时检查consumed_at即可
create table contact_claim(
    tenant_id uuid not null,
    id uuid not null,
    contact_id uuid not null,
    code_hash text not null, -- SHA-256 hex
    expires_at timestamptz not null,
    consumed_at timestamptz,
    consumed_by_user_id uuid,
    created_by_user_id uuid not null,
    created_at timestamptz not null default now(),
    -- 可选: 审计/风控
    attempts int not null default 0,
    last_attempt_at timestamptz,
    --
    primary key (tenant_id, id),
    constraint fk_claim_contact
        foreign key (tenant_id, contact_id)
        references contact (tenant_id, id),
    constraint fk_claim_creator_membership
        foreign key (created_by_user_id)
        references app_user (id),
    -- 谁消费的
    constraint fk_contact_claim_consumed_by
        foreign key (tenant_id, consumed_by_user_id)
        references tenant_membership(tenant_id, user_id),
    constraint ck_contact_claim_time check ( expires_at > created_at )
);
-- 查找未消费且过期的claim: 用hash精确命中
create index ix_contact_claim_hash_active on contact_claim(code_hash, expires_at)
    where consumed_at is null;
-- 一个contact同时只允许有一个"有效claim"(避免多码并存)
-- create unique index uq_contact_claim_active_per_contact on contact_claim(tenant_id, contact_id)
--     where consumed_at is null;
create index ix_claim_tenant_contact on contact_claim (tenant_id, contact_id);
-- 自动清理/运营使用
create index ix_claim_expires_at on contact_claim(expires_at);
-- 优化查询
create index if not exists ix_contact_claim_active_latest
    on app.contact_claim(tenant_id, contact_id, expires_at desc, created_at desc, id desc)
    where consumed_at is null;

-- 同一contact同时只能有一个未消耗claim(partial unique index)
-- [出于性能考虑, 这部分阅读放在API层来进行限制]
create unique index uq_claim_one_active_per_contact
    on contact_claim (tenant_id, contact_id)
    where consumed_at is null;
-- 需要API配合, 在产生新的code之间需要清除未消费且已过期的表项

-- AUDIT_LOG 审计日志
create table audit_log(
    tenant_id uuid not null,
    id uuid not null,
    actor_user_id uuid, -- null 表示系统
    entity_type text not null,
--         check (entity_type in (
--             'TICKET',
--             'APPOINTMENT',
--             'COMMENT',
--             'ATTACHMENT',
--             'USER',
--             'TENANT',
--             'RESOURCE_BLOCK'
--         )),
    entity_id uuid not null,
    action text not null,
    diff_json jsonb,
    request_id text,
    created_at timestamptz not null default now(),
    primary key (tenant_id, id),
    constraint fk_audit_actor_membership
        foreign key (tenant_id, actor_user_id)
        references tenant_membership(tenant_id, user_id)
);
create index ix_audit_tenant_entity on
    audit_log (tenant_id, entity_type, entity_id);
create index ix_audit_request_id on audit_log(request_id);
create index ix_audit_created_at on audit_log(tenant_id, created_at desc);

-- NOTIFICATION 通知
-- 站内通知很容易因为重试导致重复发同一条, 使用幂等dedup_key
-- 插入通知注意使用on conflict do nothing
create table notification(
    tenant_id uuid not null,
    id uuid not null,
    dedup_key text,
    user_id uuid not null,
    notification_type text not null,
    payload_json jsonb not null,
    read_at timestamptz,
    created_at timestamptz not null default now(),
    primary key (tenant_id, id),
    constraint fk_notification_user_membership
        foreign key (tenant_id, user_id)
        references tenant_membership(tenant_id, user_id)
);
create index ix_notification_user_unread
    on notification (tenant_id, user_id, read_at, created_at desc);
create unique index uq_notification_dedup
    on notification (tenant_id, user_id, dedup_key)
    where dedup_key is not null;
-- Outbox Event
-- 需要考虑幂等, 不要把同一个事件写两次仅outbox
-- <dedup_key> := <idempotency_key>:<event_discriminator>
-- insert into outbox_event(tenant_id, id, event_type, payload_json, status, dedup_key)
-- values (:tenantId, :eventId, 'TICKET_CREATED', :payload::jsonb, 'NEW', :dedupKey)
-- on conflict (tenant_id, dedup_key) do nothing; [冲突就跳过插入, 返回0行]
create table outbox_event(
    tenant_id uuid not null,
    id uuid not null,
    dedup_key text,
    event_type text not null,
    payload_json jsonb not null,
    status text not null default 'NEW'
        check (status in ('NEW','SENT', 'DEAD')),
    attempts int not null default 0,
    next_attempt_at timestamptz,
    created_at timestamptz not null default now(),
    sent_at timestamptz,
    last_error text,
    primary key (tenant_id, id)
);
create unique index uq_outbox_dedup on outbox_event(tenant_id, event_type, dedup_key);
-- WORKER拉取NEW且到期(next_attempt_at为空视为立刻可重试)
-- 这个索引支撑 WHERE tenant_id=? AND status='NEW' AND (next_attempt_at is null or next_attempt_at<=now())
-- 以及 ORDER BY created_at
create index ix_outbox_tenant_new_next_created
    on outbox_event(tenant_id, status, next_attempt_at, created_at);
-- 如果你有“跨租户统一 worker”（不按 tenant 分片），再加一个全局索引(可选)
create index ix_outbox_new_next_created
    on outbox_event(status, next_attempt_at, created_at);
-- WORKER会用到的取数SQL
-- select *
-- from outbox_event
-- where tenant_id = :tenantId
--   and status = 'NEW'
--   and (next_attempt_at is null or next_attempt_at <= now())
-- order by created_at
--     for update skip locked
-- limit :batchSize;

-- IDEMPOTENCY_RECORD 幂等支持
create table idempotency_record(
    tenant_id uuid not null,
    actor_user_id uuid not null,
    idempotency_key text not null,
    request_hash text not null,
    response_json jsonb,
    status text not null default 'IN_PROGRESS' check(
        status in ('IN_PROGRESS', 'COMPLETED')
    ),
    created_at timestamptz not null default now(),
    primary key (tenant_id, actor_user_id, idempotency_key),
    constraint fk_idem_actor_membership
        foreign key (tenant_id, actor_user_id)
        references tenant_membership(tenant_id, user_id)
);
create index ix_idem_created_at on idempotency_record(tenant_id, created_at desc);

-- APPOINTMENT
-- 依赖: tstzrange + GiST 排除约束 (防同一资源时间窗重叠)
-- appointmnet的customer xor需要与ticket的requester xor保持一致
-- 创建预约时不允许传customer, 直接从ticket requester复制(或者服务端校验一致)
create extension if not exists btree_gist;

create table appointment(
    tenant_id uuid not null,
    id uuid not null,

    ticket_id uuid not null,
    resource_user_id uuid not null,
    customer_user_id uuid, -- 注册用户
    customer_contact_id uuid, -- 外部用户
    start_at timestamptz not null,
    end_at timestamptz not null,
    -- 生成列: 半开区间
    time_range tstzrange generated always as (tstzrange(start_at, end_at, '[)')) stored,
    status text not null check ( status in ('BOOKED', 'RESCHEDULED', 'CANCELLED', 'COMPLETED') ),
    address_text text,
    notes text,
    arrived_at timestamptz,
    completed_at timestamptz,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    primary key (tenant_id, id),
    -- 基本合法性: 结束必须晚于开始
    constraint ck_appointment_time_order check(end_at > start_at),
    -- customer 必须且只能填写一个
    constraint ck_appointment_customer_xor check(
        (customer_user_id is not null) <> (customer_contact_id is not null)),
    -- ticket 必须属于同一tenant
    constraint fk_appointment_ticket
        foreign key (tenant_id, ticket_id)
        references ticket (tenant_id, id),
    -- 资源(技师)必须是该租户成员
    constraint fk_appointment_resource_membership
        foreign key (tenant_id, resource_user_id)
        references tenant_membership(tenant_id, user_id),
    -- customer_user_id如果存在, 必须为租户成员
    constraint fk_appointment_customer_user_membership
        foreign key (tenant_id, customer_user_id)
        references tenant_membership(tenant_id, user_id),
    -- customer_contact_id 如果存在, 必须是该租户contact
    constraint fk_appointment_customer_contact
        foreign key (tenant_id, customer_contact_id)
        references contact(tenant_id, id)
);

-- 核心不变量: 同一tenant+同一resource_user_id的占用时间窗口不能重叠(仅对占用状态生效)
alter table appointment
    add constraint appointment_no_overlap
    exclude using gist (
        tenant_id with =,
        resource_user_id with =,
        time_range with &&
    )
    where (status in ('BOOKED', 'RESCHEDULED'));

-- 常用索引(按资源日程, 按客户日程, 按ticket查预约)
create index ix_appointment_tenant_resource_start
    on appointment(tenant_id, resource_user_id, start_at);
create index ix_appointment_tenant_ticket
    on appointment(tenant_id, ticket_id);
create index ix_appointment_tenant_customer_user_start
    on appointment(tenant_id, customer_user_id, start_at)
    where customer_user_id is not null;
create index ix_appointment_tenant_customer_contact_start
    on appointment(tenant_id, customer_contact_id, start_at)
    where customer_contact_id is not null;
-- 可选: 如果你会按time_range做范围查询(例如查某天窗口), GiST索引很有用
create index ix_appointment_time_range_gist
    on appointment using gist (tenant_id, resource_user_id, time_range);

-- TicketComment
create table ticket_comment(
    tenant_id uuid not null,
    id uuid not null,
    ticket_id uuid not null,
    version bigint not null default 0,
    author_user_id uuid not null,
    visibility text not null default 'INTERNAL'
        check ( visibility in ('INTERNAL', 'PUBLIC') ),
    body text not null check(length(btrim(body)) > 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    edited_at timestamptz,
    deleted_at timestamptz,

    primary key (tenant_id, id),
    constraint fk_comment_ticket
        foreign key (tenant_id, ticket_id)
        references ticket(tenant_id, id)
        on delete cascade,
    constraint fk_comment_author
        foreign key (author_user_id)
        references app_user(id)
);

-- 列表: 按工单查评论(通常按时间倒序), 过滤软删
create index ix_comment_ticket_created
    on ticket_comment(tenant_id, ticket_id, created_at desc)
    where deleted_at is null;
-- 审计/我的评论: 按作者查
create index ix_comment_author_created
    on ticket_comment(tenant_id, author_user_id, created_at desc)
    where deleted_at is null;
-- 可选: 只看public评论
create index ix_comment_ticket_public
    on ticket_comment(tenant_id, ticket_id, created_at desc)
    where deleted_at is null and visibility = 'PUBLIC';

-- Attachment
create table ticket_attachment(
    tenant_id uuid not null,
    id uuid not null,
    ticket_id uuid not null,
    version bigint not null default 0,
    filename text not null check(length(btrim(filename)) > 0),
    content_type text not null check (length(btrim(content_type)) > 0),
    size_bytes bigint not null check (size_bytes>=0),
    -- 存储定位, 不把二进制放数据库
    storage_provider text not null default 'LOCAL'
        check (storage_provider in ('LOCAL', 'S3')),
    storage_key text not null check (length(btrim(storage_key)) > 0),
    sha256 varchar(64),
    uploaded_by_user_id uuid not null,
    created_at timestamptz not null default now(),
    deleted_at timestamptz,
    primary key (tenant_id, id),
    constraint fk_attachment_ticket
        foreign key (tenant_id, ticket_id)
        references ticket(tenant_id, id)
        on delete cascade,
    constraint fk_attachment_uploader
        foreign key (uploaded_by_user_id)
        references app_user(id),
    -- 可选: 同一工单内避免重复storage_key
    constraint uq_attachment_storage_key
        unique (tenant_id, ticket_id, storage_key)
);

create index ix_attachment_ticket_created
    on ticket_attachment(tenant_id, ticket_id, created_at desc)
    where deleted_at is null;
create index ix_attachment_uploader_created
    on ticket_attachment(tenant_id, uploaded_by_user_id, created_at desc)
    where deleted_at is null;
-- 可选: 按hash去重/查找
create index ix_attachment_sha256
    on ticket_attachment(tenant_id, sha256)
    where sha256 is not null and deleted_at is null;

create table resource_block(
    tenant_id uuid not null,
    id uuid not null,
    resource_user_id uuid not null,
    start_at timestamptz not null,
    end_at timestamptz not null,
    reason text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    version bigint not null default 0,

    primary key (tenant_id, id),
    constraint ck_block_time check ( end_at > start_at )
);
create index ix_block_resource_start
    on resource_block(tenant_id, resource_user_id, start_at)
    where deleted_at is null;

-- 核心不变量: 同一tenant+同一resource_user_id的block时间窗口不能重叠(仅对占用状态生效)
alter table resource_block
    add constraint resource_block_no_overlap
        exclude using gist (
        tenant_id with =,
        resource_user_id with =,
        tstzrange(start_at,end_at,'[)') with &&
    )
    where (deleted_at is null);

create table resource_working_hours(
    tenant_id uuid not null,
    resource_user_id uuid not null,
    day_of_week int not null check(day_of_week between 1 and 7),
    start_local time not null,
    end_local time not null,
    timezone text not null default 'Australia/Adelaide',
    primary key (tenant_id, resource_user_id, day_of_week),
    constraint ck_wh_time check ( end_local > start_local )
);