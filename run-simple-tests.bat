@echo off
echo 开始运行简单测试...

echo 清理项目...
call mvn clean

echo 编译项目...
call mvn compile

echo 运行简单测试...
call mvn test -Dtest="SimpleTest" -DfailIfNoTests=false

echo 运行简单用户测试...
call mvn test -Dtest="SimpleUserTest" -DfailIfNoTests=false

echo 运行简单DTO测试...
call mvn test -Dtest="SimpleLoginDTOTest" -DfailIfNoTests=false

echo 测试完成！
pause 