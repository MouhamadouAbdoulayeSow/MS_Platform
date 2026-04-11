-- Package: edu.projet.MS_Platform.auth
-- Flyway migration V1 - Create users table with OWASP security fields

CREATE TYPE user_role AS ENUM ('ETUDIANT', 'PROFESSEUR', 'ADMIN');

CREATE TABLE users (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  nom             VARCHAR(100) NOT NULL,
  email           VARCHAR(255) NOT NULL,
  mot_de_passe    VARCHAR(255) NOT NULL,
  role            user_role NOT NULL DEFAULT 'ETUDIANT',
  niveau          VARCHAR(10),
  avatar          VARCHAR(500),
  actif           BOOLEAN NOT NULL DEFAULT TRUE,
  locked          BOOLEAN NOT NULL DEFAULT FALSE,
  failed_attempts INTEGER NOT NULL DEFAULT 0,
  last_login      TIMESTAMP,
  created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_actif ON users(actif);
