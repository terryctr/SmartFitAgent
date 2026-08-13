CREATE DATABASE IF NOT EXISTS smart_study_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smart_study_agent;
CREATE TABLE IF NOT EXISTS users(id VARCHAR(64) PRIMARY KEY,name VARCHAR(100),grade VARCHAR(100),goal TEXT,weakness TEXT,preferred_style VARCHAR(200));
CREATE TABLE IF NOT EXISTS courses(id VARCHAR(64) PRIMARY KEY,title VARCHAR(200),subject VARCHAR(100),level VARCHAR(100),description TEXT,lessons INT,teacher VARCHAR(100),progress INT);
CREATE TABLE IF NOT EXISTS plans(id VARCHAR(64) PRIMARY KEY,title VARCHAR(200),subject VARCHAR(100),plan_date DATE,minutes INT,steps TEXT,status VARCHAR(100));
CREATE TABLE IF NOT EXISTS notes(id VARCHAR(64) PRIMARY KEY,title VARCHAR(200),subject VARCHAR(100),content TEXT,tags VARCHAR(200),created_at VARCHAR(64));
CREATE TABLE IF NOT EXISTS questions(id VARCHAR(64) PRIMARY KEY,subject VARCHAR(100),stem TEXT,option_a TEXT,option_b TEXT,option_c TEXT,option_d TEXT,answer VARCHAR(20),explanation TEXT);
CREATE TABLE IF NOT EXISTS attempts(id VARCHAR(64) PRIMARY KEY,subject VARCHAR(100),score INT,total INT,mistakes TEXT,created_at VARCHAR(64));
