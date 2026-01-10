-- 创建给AI使用的用户，只读权限
CREATE USER mcp_reader WITH PASSWORD 'd8JkmTSXtBwFTaK8exme';
GRANT CONNECT ON DATABASE winefoxbot TO mcp_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO mcp_reader;
-- 删除用户
DROP USER IF EXISTS mcp_writer;