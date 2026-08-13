package com.smartfitagent.db;
import java.nio.file.Path;
public final class DatabaseFactory { private DatabaseFactory(){} public static Database create(){ String mode=System.getProperty("APP_DATABASE", System.getenv().getOrDefault("APP_DATABASE","file")); if("mysql".equalsIgnoreCase(mode)){ try { return new MySqlDatabase(); } catch(RuntimeException ex){ System.out.println("MySQL不可用，切换到本地文件数据库: "+ex.getMessage()); } } return new FileDatabase(Path.of("data")); } }
