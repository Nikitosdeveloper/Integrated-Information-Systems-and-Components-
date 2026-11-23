INSERT INTO roles (code, name) VALUES
    ('ADMIN', 'Администратор'),
    ('ANALYST', 'Кредитный аналитик'),
    ('COMMITTEE', 'Член кредитного комитета'),
    ('BORROWER', 'Заёмщик');

INSERT INTO permissions (code, description) VALUES
    ('APPLICATION_CREATE', 'Создание заявки'),
    ('APPLICATION_SUBMIT', 'Подача заявки на рассмотрение'),
    ('APPLICATION_VIEW_OWN', 'Просмотр заявок своей компании'),
    ('APPLICATION_VIEW_ALL', 'Просмотр всех заявок'),
    ('APPLICATION_REVIEW', 'Рассмотрение заявок аналитиком'),
    ('DECISION_MAKE', 'Принятие кредитного решения'),
    ('STATEMENT_UPLOAD', 'Загрузка финансовой отчётности'),
    ('USER_MANAGE', 'Управление пользователями'),
    ('MODEL_MANAGE', 'Управление моделями и нормативами'),
    ('NOTIFICATION_VIEW', 'Просмотр уведомлений'),
    ('REFERENCE_VIEW', 'Просмотр справочников');

INSERT INTO role_permissions (role_code, permission_code) VALUES
    ('BORROWER', 'APPLICATION_CREATE'), ('BORROWER', 'APPLICATION_SUBMIT'), ('BORROWER', 'APPLICATION_VIEW_OWN'),
    ('BORROWER', 'STATEMENT_UPLOAD'), ('BORROWER', 'NOTIFICATION_VIEW'), ('BORROWER', 'REFERENCE_VIEW'),
    ('ANALYST', 'APPLICATION_VIEW_ALL'), ('ANALYST', 'APPLICATION_REVIEW'), ('ANALYST', 'NOTIFICATION_VIEW'), ('ANALYST', 'REFERENCE_VIEW'),
    ('COMMITTEE', 'APPLICATION_VIEW_ALL'), ('COMMITTEE', 'DECISION_MAKE'), ('COMMITTEE', 'NOTIFICATION_VIEW'), ('COMMITTEE', 'REFERENCE_VIEW'),
    ('ADMIN', 'APPLICATION_VIEW_ALL'), ('ADMIN', 'USER_MANAGE'), ('ADMIN', 'MODEL_MANAGE'), ('ADMIN', 'NOTIFICATION_VIEW'), ('ADMIN', 'REFERENCE_VIEW');
-- Пользователи создаются при первом запуске Auth Service (пароли хранятся только в виде BCrypt-хэшей).
