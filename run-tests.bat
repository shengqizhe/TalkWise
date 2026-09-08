@echo off
chcp 65001 > nul

echo ========================================
echo 开始运行完整测试 (v2)
echo ========================================

echo 1. 清理并编译项目...
call mvnw.cmd clean compile

echo.
echo 2. 运行实体类测试...

call mvnw.cmd test -Dtest="UserTest,LectureTest" -DfailIfNoTests=false

echo.
echo 3. 运行DTO测试...
call mvnw.cmd test -Dtest="LoginDTOTest,RegisterDTOTest" -DfailIfNoTests=false

echo.
echo 4. 运行服务层测试...
call mvnw.cmd test -Dtest="AuthServiceTest" -DfailIfNoTests=false

echo.
echo 5. 运行控制器测试...
call mvnw.cmd test -Dtest="AuthControllerTest" -DfailIfNoTests=false

echo.
echo ========================================
echo 测试完成！
echo ========================================
pause