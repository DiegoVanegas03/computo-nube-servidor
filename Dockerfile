FROM maven:3.9.4-eclipse-temurin-17

WORKDIR /app

COPY pom.xml .
COPY src ./src
COPY maps ./maps

RUN mvn clean compile

EXPOSE 2558

CMD ["mvn", "exec:java"]