mvn javadoc:javadoc
ROBOCOPY /MIR .\doc ..\..\..\SDK.docs\pma.java
cd ..\..\..\SDK.docs
svn commit -m "Update from jenkins build" --username Antreas --password Patho!Andre4$
powershell "Invoke-WebRequest -Uri https://docs.pathomation.com/sdk/update.php -UseBasicParsing"
mvn clean deploy