CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL
);

-- Insertar usuario de prueba
INSERT INTO users (username, password) VALUES ('admin', 'admin') ON DUPLICATE KEY UPDATE password=password;

INSERT INTO users (username, password) VALUES 
('360526', '360526'),
('321979', '321979'),
('330153', '330153'),
('303943', '303943'),
('290983', '290983'),
('339068', '339068'),
('321453', '321453'),
('346027', '346027'),
('310962', '310962'),
('342604', '342604'),
('405763', '405763'),
('338866', '338866'),
('330892', '330892')
ON DUPLICATE KEY UPDATE password=password;