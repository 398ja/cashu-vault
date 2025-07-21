CREATE SEQUENCE IF NOT EXISTS revinfo_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE revinfo
(
    rev      INTEGER NOT NULL,
    revtstmp BIGINT,
    CONSTRAINT pk_revinfo PRIMARY KEY (rev)
);

CREATE TABLE t_key
(
    id          UUID                        NOT NULL,
    archived    BOOLEAN                     NOT NULL,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    version     INTEGER,
    amount      DECIMAL                     NOT NULL,
    private_key VARCHAR(255)                NOT NULL,
    key_set_id  UUID,
    CONSTRAINT pk_t_key PRIMARY KEY (id)
);

CREATE TABLE t_key_a
(
    rev         INTEGER NOT NULL,
    revtype     SMALLINT,
    id          UUID    NOT NULL,
    archived    BOOLEAN,
    created_at  TIMESTAMP WITHOUT TIME ZONE,
    updated_at  TIMESTAMP WITHOUT TIME ZONE,
    amount      DECIMAL,
    private_key VARCHAR(255),
    key_set_id  UUID,
    CONSTRAINT pk_t_key_a PRIMARY KEY (rev, id)
);

CREATE TABLE t_keyset
(
    id         UUID                        NOT NULL,
    archived   BOOLEAN                     NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    version    INTEGER,
    key_set_id VARCHAR(16)                 NOT NULL,
    unit       VARCHAR(5)                  NOT NULL,
    mint_id    UUID,
    CONSTRAINT pk_t_keyset PRIMARY KEY (id)
);

CREATE TABLE t_keyset_a
(
    rev        INTEGER NOT NULL,
    revtype    SMALLINT,
    id         UUID    NOT NULL,
    archived   BOOLEAN,
    created_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    key_set_id VARCHAR(16),
    unit       VARCHAR(5),
    mint_id    UUID,
    CONSTRAINT pk_t_keyset_a PRIMARY KEY (rev, id)
);

CREATE TABLE t_mint
(
    id         UUID                        NOT NULL,
    archived   BOOLEAN                     NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    version    INTEGER,
    CONSTRAINT pk_t_mint PRIMARY KEY (id)
);

CREATE TABLE t_mint_a
(
    rev        INTEGER NOT NULL,
    revtype    SMALLINT,
    id         UUID    NOT NULL,
    archived   BOOLEAN,
    created_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_t_mint_a PRIMARY KEY (rev, id)
);

CREATE TABLE t_proof
(
    id         UUID                        NOT NULL,
    archived   BOOLEAN                     NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    version    INTEGER,
    mint_id    UUID                        NOT NULL,
    amount     INTEGER                     NOT NULL,
    secret     VARCHAR(255)                NOT NULL,
    c          VARCHAR(255)                NOT NULL,
    witness    VARCHAR(255),
    state      VARCHAR(255)                NOT NULL,
    CONSTRAINT pk_t_proof PRIMARY KEY (id)
);

CREATE TABLE t_proof_a
(
    rev        INTEGER NOT NULL,
    revtype    SMALLINT,
    id         UUID    NOT NULL,
    archived   BOOLEAN,
    created_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    mint_id    UUID,
    amount     INTEGER,
    secret     VARCHAR(255),
    c          VARCHAR(255),
    witness    VARCHAR(255),
    state      VARCHAR(255),
    CONSTRAINT pk_t_proof_a PRIMARY KEY (rev, id)
);

ALTER TABLE t_keyset
    ADD CONSTRAINT uc_t_keyset_key_set UNIQUE (key_set_id);

ALTER TABLE t_proof
    ADD CONSTRAINT uc_t_proof_witness UNIQUE (witness);

CREATE UNIQUE INDEX idx_key_private_key_unq ON t_key (private_key);

CREATE UNIQUE INDEX idx_keyset_sat_mint_unq ON t_keyset (unit, mint_id);

ALTER TABLE t_keyset_a
    ADD CONSTRAINT FK_T_KEYSET_A_ON_REV FOREIGN KEY (rev) REFERENCES revinfo (rev);

ALTER TABLE t_keyset
    ADD CONSTRAINT FK_T_KEYSET_ON_MINT FOREIGN KEY (mint_id) REFERENCES t_mint (id);

ALTER TABLE t_key_a
    ADD CONSTRAINT FK_T_KEY_A_ON_REV FOREIGN KEY (rev) REFERENCES revinfo (rev);

ALTER TABLE t_key
    ADD CONSTRAINT FK_T_KEY_ON_KEY_SET FOREIGN KEY (key_set_id) REFERENCES t_keyset (id);

ALTER TABLE t_mint_a
    ADD CONSTRAINT FK_T_MINT_A_ON_REV FOREIGN KEY (rev) REFERENCES revinfo (rev);

ALTER TABLE t_proof_a
    ADD CONSTRAINT FK_T_PROOF_A_ON_REV FOREIGN KEY (rev) REFERENCES revinfo (rev);

ALTER TABLE t_proof
    ADD CONSTRAINT FK_T_PROOF_ON_MINT FOREIGN KEY (mint_id) REFERENCES t_mint (id);

CREATE INDEX idx_proof_mint_id ON t_proof (mint_id);