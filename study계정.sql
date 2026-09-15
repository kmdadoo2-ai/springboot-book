alter session set "_ORACLE_SCRIPT"=true;

CREATE USER study IDENTIFIED BY 1234;
GRANT CONNECT, RESOURCE TO study; 
ALTER USER study DEFAULT TABLESPACE users QUOTA UNLIMITED ON users;

-- Flyway 실습 사용자 생성
CREATE USER spring_flyway
IDENTIFIED BY 1234
DEFAULT TABLESPACE users
TEMPORARY TABLESPACE temp
QUOTA 100M ON users;

GRANT CREATE SESSION, CREATE TABLE TO spring_flyway;

GRANT CREATE SEQUENCE TO spring_flyway;

SELECT table_name
FROM user_tables
ORDER BY table_name;

SELECT "installed_rank",
       "version",
       "description",
       "script",
       "success"
FROM "flyway_schema_history"
ORDER BY "installed_rank";

SELECT *
FROM "flyway_schema_history"
ORDER BY "installed_rank";

SELECT "installed_rank", "version", "description", "success"
FROM "flyway_schema_history"
ORDER BY "installed_rank";

SELECT COUNT(*) FROM board;
SELECT COUNT(*) FROM site_user;

SELECT index_name
FROM user_indexes
WHERE table_name = 'BOARD';
