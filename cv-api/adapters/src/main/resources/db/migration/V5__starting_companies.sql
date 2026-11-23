-- The companies crawled first, each board checked live on 2026-11-22. The admin zone keeps the list from
-- slice 6. See decisions/jobs.md.
INSERT INTO tracked_companies (name, source, board_token) VALUES
    ('Monzo',      'GREENHOUSE', 'monzo'),
    ('GitLab',     'GREENHOUSE', 'gitlab'),
    ('Cloudflare', 'GREENHOUSE', 'cloudflare'),
    ('Palantir',   'LEVER',      'palantir'),
    ('Spotify',    'LEVER',      'spotify'),
    ('Ramp',       'ASHBY',      'ramp'),
    ('Linear',     'ASHBY',      'linear'),
    ('Blueground', 'WORKABLE',   'blueground');
