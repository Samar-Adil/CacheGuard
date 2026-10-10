@echo off
cd /d "%~dp0.."
if not exist target\cacheguard-1.0.0.jar call mvn -B -ntp -DskipTests package
java -jar target\cacheguard-1.0.0.jar %*
