INSERT INTO products (sku, name, price, active) VALUES
    ('MOUSE-1', 'Wireless mouse', 24.90, TRUE),
    ('KBD-1', 'Mechanical keyboard', 89.00, TRUE),
    ('MON-27', '27" monitor', 279.00, TRUE),
    ('HDMI-2M', 'HDMI cable 2 m', 9.50, TRUE),
    ('CAM-720', '720p webcam', 19.00, FALSE);

INSERT INTO stock (sku, available) VALUES
    ('MOUSE-1', 100),
    ('KBD-1', 40),
    ('MON-27', 5),
    ('HDMI-2M', 200),
    ('CAM-720', 0);
