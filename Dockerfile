FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN addgroup --system multiapp && adduser --system --ingroup multiapp multiapp
COPY --from=build /app/target/*.jar /app/app.jar

RUN mkdir -p /data/uploads && chown -R multiapp:multiapp /data/uploads
USER multiapp

EXPOSE 8081

ENV JAVA_OPTS="-Xms256m -Xmx768m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]