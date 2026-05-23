insert into accounts (id, balance, currency, version)
values
    ('11111111-1111-1111-1111-111111111111', 1000.00, 'USD', 0),
    ('22222222-2222-2222-2222-222222222222', 100.00, 'USD', 0)
on conflict (id) do nothing;
