CREATE TABLE IF NOT EXISTS event_journal(
                                            ordering           BIGINT       AUTO_INCREMENT,
                                            deleted            BOOLEAN      DEFAULT false NOT NULL,
                                            persistence_id     VARCHAR(255) NOT NULL,
    sequence_number    BIGINT       NOT NULL,
    writer             TEXT         NOT NULL,
    write_timestamp    BIGINT       NOT NULL,
    adapter_manifest   TEXT         NOT NULL,
    event_payload      BLOB         NOT NULL,
    event_ser_id       INTEGER      NOT NULL,
    event_ser_manifest TEXT         NOT NULL,
    meta_payload       BLOB,
    meta_ser_id        INTEGER,
    meta_ser_manifest  TEXT,
    PRIMARY KEY(persistence_id, sequence_number),
    CONSTRAINT event_journal_ordering_uq UNIQUE (ordering)
    );

CREATE TABLE IF NOT EXISTS event_tag(
                                        event_id BIGINT,
                                        tag      VARCHAR(255),
    PRIMARY KEY(event_id, tag)
    );

CREATE TABLE IF NOT EXISTS snapshot(
                                       persistence_id        VARCHAR(255) NOT NULL,
    sequence_number       BIGINT       NOT NULL,
    created               BIGINT       NOT NULL,
    snapshot_ser_id       INTEGER      NOT NULL,
    snapshot_ser_manifest TEXT         NOT NULL,
    snapshot_payload      BLOB         NOT NULL,
    meta_ser_id           INTEGER,
    meta_ser_manifest     TEXT,
    meta_payload          BLOB,
    PRIMARY KEY(persistence_id, sequence_number)
    );