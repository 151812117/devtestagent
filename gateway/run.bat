@echo off
chcp 65001 >nul
set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8
set JAVA_HOME=C:/Program Files/Java/jdk1.8.0_144
mvn spring-boot:run