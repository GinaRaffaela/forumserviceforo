SELECT 'APP_USERS' AS tabla, COUNT(*) AS registros FROM app_users;
SELECT id, display_name, email, role FROM app_users ORDER BY id;

SELECT 'FORUM_CATEGORIES' AS tabla, COUNT(*) AS registros FROM forum_categories;
SELECT 'FORUM_TOPICS' AS tabla, COUNT(*) AS registros FROM forum_topics;
SELECT 'FORUM_COMMENTS' AS tabla, COUNT(*) AS registros FROM forum_comments;
SELECT id, name FROM forum_categories ORDER BY id;
SELECT id, title, author_id, category_id FROM forum_topics ORDER BY id;
SELECT id, author_id, topic_id FROM forum_comments ORDER BY id;