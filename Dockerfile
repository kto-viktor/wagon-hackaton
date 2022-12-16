FROM openjdk:17-jdk-alpine
ARG JAR_FILE=build/libs/wagonvoice-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar
RUN mkdir data
ENTRYPOINT ["java","-jar","/app.jar"]