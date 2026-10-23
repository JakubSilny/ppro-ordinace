CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE TABLE insurer (
    id   BIGSERIAL PRIMARY KEY,
    code VARCHAR(3)   NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE patient (
    id           BIGSERIAL PRIMARY KEY,
    first_name   VARCHAR(100) NOT NULL,
    last_name    VARCHAR(100) NOT NULL,
    birth_year   INT          NOT NULL CHECK (birth_year BETWEEN 1900 AND 2100),
    phone        VARCHAR(20)  NOT NULL CHECK (phone ~ '^[0-9]+$'),
    insurance_id VARCHAR(20)  UNIQUE,
    insurer_id   BIGINT       REFERENCES insurer (id),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_patient_last_name ON patient (lower(last_name) text_pattern_ops);
CREATE INDEX idx_patient_phone ON patient (phone);

CREATE TABLE doctor (
    id         BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE office_hours (
    id        BIGSERIAL PRIMARY KEY,
    doctor_id BIGINT       NOT NULL REFERENCES doctor (id),
    weekday   SMALLINT     NOT NULL CHECK (weekday BETWEEN 1 AND 7),
    from_time TIME         NOT NULL,
    to_time   TIME         NOT NULL,
    location  VARCHAR(100) NOT NULL,
    bookable  BOOLEAN      NOT NULL DEFAULT TRUE,
    CHECK (to_time > from_time)
);
CREATE INDEX idx_office_hours_doctor ON office_hours (doctor_id, weekday);

CREATE TABLE doctor_absence (
    id         BIGSERIAL PRIMARY KEY,
    doctor_id  BIGINT      NOT NULL REFERENCES doctor (id),
    start_at   TIMESTAMPTZ NOT NULL,
    end_at     TIMESTAMPTZ NOT NULL,
    reason     VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (end_at > start_at)
);
CREATE INDEX idx_absence_doctor ON doctor_absence (doctor_id, start_at);

CREATE TABLE appointment (
    id                  BIGSERIAL PRIMARY KEY,
    doctor_id           BIGINT      NOT NULL REFERENCES doctor (id),
    patient_id          BIGINT      NOT NULL REFERENCES patient (id),
    start_at            TIMESTAMPTZ NOT NULL,
    end_at              TIMESTAMPTZ NOT NULL,
    status              VARCHAR(20) NOT NULL
        CHECK (status IN ('BOOKED', 'COMPLETED', 'CANCELLED', 'NO_SHOW', 'RESCHEDULED')),
    needs_rebooking     BOOLEAN     NOT NULL DEFAULT FALSE,
    reason              VARCHAR(200),
    rescheduled_from_id BIGINT      REFERENCES appointment (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (end_at > start_at),
    CONSTRAINT appointment_doctor_no_overlap EXCLUDE USING gist (
        doctor_id WITH =, tstzrange(start_at, end_at) WITH &&
    ) WHERE (status IN ('BOOKED', 'COMPLETED', 'NO_SHOW')),
    CONSTRAINT appointment_patient_no_overlap EXCLUDE USING gist (
        patient_id WITH =, tstzrange(start_at, end_at) WITH &&
    ) WHERE (status IN ('BOOKED', 'COMPLETED', 'NO_SHOW'))
);
CREATE INDEX idx_appointment_doctor_start ON appointment (doctor_id, start_at);
CREATE INDEX idx_appointment_patient_start ON appointment (patient_id, start_at);

CREATE TABLE visit (
    id             BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT      NOT NULL UNIQUE REFERENCES appointment (id),
    note           VARCHAR(500),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE procedure_item (
    id        BIGSERIAL PRIMARY KEY,
    code      VARCHAR(10)  NOT NULL UNIQUE,
    name      VARCHAR(200) NOT NULL,
    points    INT          NOT NULL CHECK (points >= 0),
    countable BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE visit_procedure (
    id             BIGSERIAL PRIMARY KEY,
    visit_id       BIGINT      NOT NULL REFERENCES visit (id),
    procedure_id   BIGINT      NOT NULL REFERENCES procedure_item (id),
    quantity       INT         NOT NULL CHECK (quantity >= 1),
    points_at_time INT         NOT NULL CHECK (points_at_time >= 0),
    voided_at      TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_visit_procedure_active ON visit_procedure (visit_id, procedure_id) WHERE voided_at IS NULL;
