--{"user_id": "543462", "item_id":"1715", "category_id": "1464116", "behavior": "pv", "ts": "2017-11-26T01:00:00Z"}
--{"user_id": "662867", "item_id":"2244074", "category_id": "1575622", "behavior": "pv", "ts": "2017-11-26T01:00:00Z"}

CREATE TABLE user_log (
    user_id VARCHAR,
    item_id VARCHAR,
    category_id VARCHAR,
    behavior VARCHAR,
    ts TIMESTAMP
)WITH(
    'connector.type' = 'kafka',-- 使用 kafka connector
    'connector.version' = 'universal',-- kafka 版本，universal 支持 0.11 以上的版本
    'connector.topic' = 'user_behavior',-- kafka topic
    'connector.startup-mode' = 'earliest-offset',-- 从起始 offset 开始读取
    'connector.properties.0.key' = 'bootstrap.servers',-- 连接信息
    'connector.properties.0.value' = 'localhost:9092',
    'update-mode' = 'append',
    'format.type' = 'json',-- 数据源格式为 json
    'format.derive-schema' = 'true'-- 从 DDL schema 确定 json 解析规则
);

CREATE TABLE pvuv_sink (
    dt VARCHAR,
    pv BIGINT,
    uv BIGINT
) WITH (
    'connector.type' = 'jdbc', -- 使用 jdbc connector
    'connector.url' = 'jdbc:mysql://localhost:3306/test', -- jdbc url
    'connector.table' = 'pvuv_sink', -- 表名
    'connector.username' = 'root', -- 用户名
    'connector.password' = '123456', -- 密码
    'connector.write.flush.max-rows' = '1' -- 默认5000条，为了演示改为1条
);

INSERT INTO pvuv_sink
SELECT
  category_id AS dt,
  COUNT(*) AS pv,
  COUNT(DISTINCT user_id) AS uv
FROM user_log
GROUP BY category_id;