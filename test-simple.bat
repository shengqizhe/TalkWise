@echo off
chcp 65001 > nul

echo ========================================
echo 开始运行简单测试 (v2)
echo ========================================

echo 1. 清理项目...
call mvnw.cmd clean

echo.
echo 2. 编译项目...
call mvnw.cmd compile

echo.
echo 3. 运行简单测试...
call mvnw.cmd test -Dtest=SimpleTest

echo.
echo 4. 运行简单用户测试...
call mvnw.cmd test -Dtest=SimpleUserTest

echo.
echo 5. 运行DTO测试...
call mvnw.cmd test -Dtest=SimpleLoginDTOTest

echo.
echo ========================================
echo 测试完成！
echo ========================================
pause