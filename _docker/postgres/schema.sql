CREATE TABLE application.public.user
(
    id             SERIAL PRIMARY KEY,
    username       VARCHAR(40) UNIQUE,
    email          VARCHAR(40) UNIQUE,
    password       VARCHAR(200) DEFAULT NULL,
    role           VARCHAR(40)  DEFAULT NULL,
    enabled        BOOLEAN      DEFAULT NULL,
    last_active_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,

    created_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    deleted_at     TIMESTAMP    DEFAULT NULL,
    created_by     integer      DEFAULT NULL,
    updated_by     integer      DEFAULT NULL,
    deleted_by     integer      DEFAULT NULL
);

CREATE TABLE application.public.user_authority
(
    id        SERIAL PRIMARY KEY,
    user_id   INT         DEFAULT NULL,
    authority VARCHAR(40) DEFAULT NULL
);

CREATE UNIQUE INDEX user_authority_unique_idx
    ON application.public.user_authority (user_id, authority);


INSERT INTO application.public."user" (username, email, password, role, enabled)
VALUES ('1ambda', '1ambda@github.com', NULL, 'ADMIN', true);

INSERT INTO application.public.user_authority (user_id, authority)
VALUES (1, 'PERMISSION_A'),
       (1, 'PERMISSION_B')
;

CREATE TABLE application.public.audit_resource_history
(
    id               BIGSERIAL PRIMARY KEY,
    user_id          INT       DEFAULT NULL,
    resource_type    VARCHAR(40) NOT NULL,
    resource_id      INT         NOT NULL,
    resource_content JSON        NOT NULL,
    action           VARCHAR(40) NOT NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE application.public.audit_access_history
(
    id             BIGSERIAL PRIMARY KEY,
    user_id        INT       DEFAULT NULL,
    access_type    VARCHAR(40) NOT NULL,
    access_content JSON        NOT NULL,
    action         VARCHAR(40) NOT NULL,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE application.public.resource
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    INT       DEFAULT NULL,
    resource   VARCHAR(40) NOT NULL,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP DEFAULT NULL,
    created_by integer   DEFAULT NULL,
    updated_by integer   DEFAULT NULL,
    deleted_by integer   DEFAULT NULL
);



GRANT ALL PRIVILEGES ON DATABASE application TO application;
GRANT ALL ON ALL TABLES IN SCHEMA public TO application;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO application;



