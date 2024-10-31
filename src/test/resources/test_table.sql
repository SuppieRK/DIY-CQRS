CREATE
  TABLE
    IF NOT EXISTS test_table(
      id UUID PRIMARY KEY,
      created_at TIMESTAMP NOT NULL
    );