from app.core.config import Settings


def make(**env):
    return Settings(_env_file=None, **env)


def test_missing_secret_key_is_derived_from_database_url_and_stable():
    a = make(DATABASE_URL="postgresql://u:p@db-a/x", SECRET_KEY="")
    again = make(DATABASE_URL="postgresql://u:p@db-a/x", SECRET_KEY="")
    other = make(DATABASE_URL="postgresql://u:p@db-b/x", SECRET_KEY="")
    assert len(a.SECRET_KEY) == 64
    assert a.SECRET_KEY == again.SECRET_KEY  # logins survive restarts
    assert a.SECRET_KEY != other.SECRET_KEY
    assert "u:p@db-a" not in a.SECRET_KEY


def test_explicit_secret_key_wins():
    assert make(DATABASE_URL="postgresql://u:p@db/x", SECRET_KEY="chosen").SECRET_KEY == "chosen"
