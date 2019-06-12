FROM maven:3-jdk-8-alpine as builder
RUN apk update && apk add protobuf
RUN apk add grpc-java --repository=http://dl-cdn.alpinelinux.org/alpine/edge/testing
WORKDIR /app
COPY . .
RUN mvn -DskipTests=true -P headless package
RUN mkdir -p html/ui/doc && mvn javadoc:javadoc-no-fork && cp -r target/site/apidocs/* html/ui/doc

FROM openjdk:8-jre-alpine
RUN apk update && apk upgrade && apk add --no-cache bash
WORKDIR /app
COPY --from=builder /app/dist/tmp/burst.jar .
COPY --from=builder /app/html html
VOLUME ["/conf"]
COPY conf/brs.properties.mariadb /conf/brs.properties
COPY conf/brs-default.properties /conf/brs-default.properties
COPY conf/logging-default.properties /conf/logging-default.properties
EXPOSE 8125 8123 8121
ENTRYPOINT ["java", "-classpath", "/app/burst.jar:/conf", "brs.Burst"]
