# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Cache dependencies first
COPY pom.xml ./
COPY common/pom.xml common/pom.xml
COPY namenode/pom.xml namenode/pom.xml
COPY datanode/pom.xml datanode/pom.xml
COPY client/pom.xml client/pom.xml
RUN mvn -B -q -e -ntp -pl common,namenode,datanode,client -am dependency:go-offline || true

# Copy sources and build
COPY common/src common/src
COPY namenode/src namenode/src
COPY datanode/src datanode/src
COPY client/src client/src
RUN mvn -B -q -e -ntp -DskipTests package

# ---- Runtime stage (selected by ARG MODULE) ----
FROM eclipse-temurin:17-jre AS runtime
ARG MODULE
ENV MODULE=${MODULE}
WORKDIR /app

# Copy whichever module's JAR was built
COPY --from=build /workspace/${MODULE}/target/${MODULE}.jar /app/app.jar

EXPOSE 8080 8081 8082 8083
ENV JAVA_OPTS=""
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
