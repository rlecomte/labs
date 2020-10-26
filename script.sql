CREATE TABLE streams(
    stream_id UUID NOT NULL,
    version bigint NOT NULL,
    PRIMARY KEY (stream_id)
);

CREATE TABLE IF NOT EXISTS events(
    seq_num bigserial NOT NULL,
    stream_id uuid NOT NULL,
    version int NOT NULL,
    payload jsonb NOT NULL,
    aggregate_type text NOT NULL,
    event_type text NOT NULL,
    metadata jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (seq_num),
    UNIQUE (stream_id, version),
    FOREIGN KEY (stream_id)
        REFERENCES streams (stream_id)
);
