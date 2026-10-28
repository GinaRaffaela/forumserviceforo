WHENEVER SQLERROR EXIT SQL.SQLCODE

CREATE SEQUENCE categories_seq START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE topics_seq START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE comments_seq START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE forum_categories (
    id NUMBER(19,0) PRIMARY KEY,
    name VARCHAR2(80 CHAR) NOT NULL,
    description VARCHAR2(500 CHAR) NOT NULL,
    CONSTRAINT uk_categories_name UNIQUE (name)
);
CREATE UNIQUE INDEX uk_categories_name_ci ON forum_categories (LOWER(name));

CREATE TABLE forum_topics (
    id NUMBER(19,0) PRIMARY KEY,
    title VARCHAR2(180 CHAR) NOT NULL,
    content CLOB NOT NULL,
    author_id NUMBER(19,0) NOT NULL,
    category_id NUMBER(19,0) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_topic_category FOREIGN KEY (category_id) REFERENCES forum_categories(id)
);
CREATE INDEX idx_topics_category ON forum_topics (category_id);
CREATE INDEX idx_topics_created ON forum_topics (created_at, id);

CREATE TABLE forum_comments (
    id NUMBER(19,0) PRIMARY KEY,
    content CLOB NOT NULL,
    author_id NUMBER(19,0) NOT NULL,
    topic_id NUMBER(19,0) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_comment_topic FOREIGN KEY (topic_id) REFERENCES forum_topics(id)
);
CREATE INDEX idx_comments_topic ON forum_comments (topic_id, created_at, id);