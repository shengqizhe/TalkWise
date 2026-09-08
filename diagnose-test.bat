@echo off
chcp 65001 > nul

echo ========================================
echo 测试诊断脚本 (v2)
echo ========================================

echo 1. 检查Java版本...
java -version

echo.
echo 2. 检查Maven版本...
call mvnw.cmd -version

echo.
echo 3. 检查项目结构...
if exist "src\test\java" ( echo  - src/test/java 目录存在 ) else ( echo  - src/test/java 目录不存在 )

echo.
echo 4. 检查测试配置文件...
if exist "src\test\resources\application-test.yml" ( echo  - application-test.yml 存在 ) else ( echo  - application-test.yml 不存在 )

echo.
echo 5. 尝试编译项目...
call mvnw.cmd compile

echo.
echo 6. 尝试运行最简单的测试...
call mvnw.cmd test -Dtest=SimpleTest -DfailIfNoTests=false

echo.
echo ========================================
echo 诊断完成！
echo ========================================
pause