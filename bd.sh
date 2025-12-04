export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64/ 
./gradlew :javaagent:shadowJar -x test -x check --no-daemon --no-build-cache

